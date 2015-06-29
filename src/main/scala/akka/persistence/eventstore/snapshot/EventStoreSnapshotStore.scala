package akka.persistence.eventstore.snapshot

import akka.persistence.eventstore.Helpers._
import akka.persistence.eventstore.{ EventStorePlugin, UrlEncoder }
import akka.persistence.snapshot.SnapshotStore
import akka.persistence.{ SelectedSnapshot, SnapshotMetadata, SnapshotSelectionCriteria }
import eventstore.ReadDirection.Backward
import eventstore._

import scala.concurrent.Future

class EventStoreSnapshotStore extends SnapshotStore with EventStorePlugin {
  import EventStoreSnapshotStore.SnapshotEvent._
  import EventStoreSnapshotStore._
  import context.dispatcher

  val readBatchSize: Int = config.getInt("read-batch-size")

  def config = context.system.settings.config.getConfig("eventstore.persistence.snapshot-store")

  def loadAsync(persistenceId: PersistenceId, criteria: SnapshotSelectionCriteria) = async {
    import Selection._
    def fold(deletes: Deletes, event: Event): Selection = {
      deserialize(event, classOf[SnapshotEvent]) match {
        case Delete(seqNr, _) => deletes.copy(deleted = deletes.deleted + seqNr)

        case DeleteCriteria(maxSeqNr, maxTimestamp) => deletes.copy(
          minSequenceNr = math.max(deletes.minSequenceNr, maxSeqNr),
          minTimestamp = math.max(deletes.minTimestamp, maxTimestamp))

        case Snapshot(snapshot, metadata) =>
          val seqNr = metadata.sequenceNr
          val timestamp = metadata.timestamp

          val deleted = seqNr <= deletes.minSequenceNr ||
            timestamp <= deletes.minTimestamp ||
            (deletes.deleted contains seqNr)

          val acceptable = seqNr <= criteria.maxSequenceNr && timestamp <= criteria.maxTimestamp

          if (deleted || !acceptable) deletes
          else Selected(SelectedSnapshot(metadata, snapshot))
      }
    }

    val streamId = eventStream(persistenceId)
    val req = ReadStreamEvents(streamId, EventNumber.Last, maxCount = readBatchSize, direction = Backward)
    connection.foldLeft(req, Empty) { case (deletes: Deletes, event) => fold(deletes, event) }.map(_.selected)
  }

  def saveAsync(metadata: SnapshotMetadata, snapshot: Any) = asyncUnit {
    val streamId = eventStream(metadata.persistenceId)
    connection.future(WriteEvents(streamId, List(serialize(Snapshot(snapshot, metadata), Some(snapshot)))))
  }

  def saved(metadata: SnapshotMetadata) = {}

  def deleteAsync(metadata: SnapshotMetadata): Future[Unit] = {
    delete(metadata.persistenceId, Delete(metadata.sequenceNr, timestamp = metadata.timestamp)).map(_ => ())
  }

  def deleteAsync(persistenceId: PersistenceId, criteria: SnapshotSelectionCriteria): Future[Unit] = {
    delete(persistenceId, SnapshotEvent.DeleteCriteria(
      maxSequenceNr = criteria.maxSequenceNr,
      maxTimestamp = criteria.maxTimestamp)).map(_ => ())
  }

  def eventStream(x: PersistenceId): EventStream.Id = EventStream.Id(prefix + UrlEncoder(x) + "-snapshots")

  def delete(persistenceId: PersistenceId, se: DeleteEvent): Future[WriteEventsCompleted] = {
    val streamId = eventStream(persistenceId)
    connection.future(WriteEvents(streamId, List(serialize(se, None))))
  }
}

object EventStoreSnapshotStore {
  sealed trait SnapshotEvent extends Serializable

  object SnapshotEvent {
    @SerialVersionUID(1)
    case class Snapshot(data: Any, metadata: SnapshotMetadata) extends SnapshotEvent

    sealed trait DeleteEvent extends SnapshotEvent

    @SerialVersionUID(0)
    case class Delete(sequenceNr: SequenceNr, timestamp: Timestamp) extends DeleteEvent

    @SerialVersionUID(0)
    case class DeleteCriteria(maxSequenceNr: SequenceNr, maxTimestamp: Timestamp) extends DeleteEvent
  }

  sealed trait Selection {
    def selected: Option[SelectedSnapshot]
  }

  object Selection {
    val Empty: Selection = Deletes(Set.empty, -1L, -1L)

    case class Deletes(
        deleted: Set[SequenceNr],
        minSequenceNr: SequenceNr,
        minTimestamp: Timestamp) extends Selection {
      def selected = None
    }

    case class Selected(value: SelectedSnapshot) extends Selection {
      def selected = Some(value)
    }
  }
}