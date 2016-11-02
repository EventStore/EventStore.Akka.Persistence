package akka.persistence.eventstore.snapshot

import akka.persistence.eventstore.EventStorePlugin
import akka.persistence.eventstore.Helpers._
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
    def fold(d: Deletes, event: Event): Selection = {
      serialization.deserialize[SnapshotEvent](event) match {
        case x: Delete         => d.copy(snapshots = x :: d.snapshots)
        case x: DeleteCriteria => d.copy(criteria = x :: d.criteria)
        case Snapshot(snapshot, metadata) =>
          if (!(criteria matches metadata) || (d deleted metadata)) d
          else Selected(SelectedSnapshot(metadata, snapshot))
      }
    }

    val streamId = eventStream(persistenceId)
    val req = ReadStreamEvents(streamId, EventNumber.Last, maxCount = readBatchSize, direction = Backward)
    connection.foldLeft(req, Empty) { case (deletes: Deletes, event) => fold(deletes, event) }.map(_.selected)
  }

  def saveAsync(metadata: SnapshotMetadata, snapshot: Any) = async {
    for {
      event <- Future { serialization.serialize(Snapshot(snapshot, metadata), Some(snapshot)) }
      streamId = eventStream(metadata.persistenceId)
      _ <- connection(WriteEvents(streamId, List(event)))
    } yield ()
  }

  def deleteAsync(metadata: SnapshotMetadata) = {
    delete(metadata.persistenceId, Delete(metadata.sequenceNr, timestamp = metadata.timestamp))
  }

  def deleteAsync(persistenceId: PersistenceId, criteria: SnapshotSelectionCriteria) = {
    val deleteCriteria = SnapshotEvent.DeleteCriteria(
      maxSequenceNr = criteria.maxSequenceNr,
      maxTimestamp = criteria.maxTimestamp,
      minSequenceNr = criteria.minSequenceNr,
      minTimestamp = criteria.minTimestamp
    )
    delete(persistenceId, deleteCriteria)
  }

  def eventStream(x: PersistenceId): EventStream.Id = EventStream.Id(prefix + x + "-snapshots")

  def delete(persistenceId: PersistenceId, se: DeleteEvent): Future[Unit] = async {
    for {
      event <- Future { serialization.serialize(se, None) }
      streamId = eventStream(persistenceId)
      _ <- connection(WriteEvents(streamId, List(event)))
    } yield ()
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

    @SerialVersionUID(1)
    case class DeleteCriteria(
      maxSequenceNr: SequenceNr,
      maxTimestamp:  Timestamp,
      minSequenceNr: SequenceNr,
      minTimestamp:  Timestamp
    ) extends DeleteEvent
  }

  sealed trait Selection {
    def selected: Option[SelectedSnapshot]
  }

  object Selection {
    val Empty: Selection = Deletes(Nil, Nil)

    case class Deletes(
        snapshots: List[SnapshotEvent.Delete],
        criteria:  List[SnapshotEvent.DeleteCriteria]
    ) extends Selection {

      def selected = None

      def deleted(metadata: SnapshotMetadata): Boolean = {
        import metadata._
        def matchesSnapshot = snapshots exists {
          case SnapshotEvent.Delete(nr, ts) =>
            nr == sequenceNr && (ts == 0L || ts == timestamp)
        }

        def matchesCriteria = criteria exists {
          case SnapshotEvent.DeleteCriteria(maxNr, maxTs, minNr, minTs) =>
            sequenceNr >= minNr && sequenceNr <= maxNr && timestamp >= minTs && timestamp <= maxTs
        }

        matchesSnapshot || matchesCriteria
      }
    }

    case class Selected(value: SelectedSnapshot) extends Selection {
      def selected = Some(value)
    }
  }
}