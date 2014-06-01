package akka.persistence.eventstore.snapshot

import akka.actor.ActorLogging
import akka.persistence.snapshot.SnapshotStore
import akka.persistence.{ SelectedSnapshot, SnapshotMetadata, SnapshotSelectionCriteria }
import akka.persistence.eventstore.Helpers._
import akka.serialization.{ SerializationExtension, Serialization }
import java.util.concurrent.TimeUnit
import scala.util.{ Success, Failure }
import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._
import eventstore._
import eventstore.ReadDirection.Backward

class EventStoreSnapshotStore extends SnapshotStore with ActorLogging {
  import EventStoreSnapshotStore._
  import EventStoreSnapshotStore.SnapshotEvent._
  import context.dispatcher

  val config = context.system.settings.config.getConfig("eventstore.snapshot-store")
  val deleteAwait = config.getDuration("delete-await", TimeUnit.MILLISECONDS).millis
  val connection: EsConnection = EsConnection(context.system)
  val serialization: Serialization = SerializationExtension(context.system)

  def loadAsync(processorId: String, criteria: SnapshotSelectionCriteria) = {
    import Batch._

    def loopEvent(event: Event, state: State): State = {
      event.data.eventType match {
        case "snapshot" =>
          // TODO
          SnapshotEvent1.unapply(event) match {
            case None => state
            case Some(metadata) =>
              val seqNr = metadata.sequenceNr
              val timestamp = metadata.timestamp

              val deleted = seqNr <= state.minSequenceNr ||
                timestamp <= state.minTimestamp ||
                (state.deleted contains seqNr)

              val acceptable = seqNr <= criteria.maxSequenceNr && timestamp <= criteria.maxTimestamp

              if (deleted || !acceptable) state
              else {
                // TODO
                val snapshot = serialization.deserialize(event.data.data.value.toArray, classOf[Snapshot]).get
                state.copy(event = Some(SelectedSnapshot(metadata, snapshot.data)))
              }
          }

        case et =>
          val snapshotEvent = serialization.deserialize(event.data.data.value.toArray, clazz(et)).get
          snapshotEvent match {
            case Delete(seqNr, _) => state.copy(deleted = state.deleted + seqNr)
            case DeleteCriteria(maxSeqNr, maxTimestamp) => state.copy(
              minSequenceNr = math.max(state.minSequenceNr, maxSeqNr),
              minTimestamp = math.max(state.minTimestamp, maxTimestamp))
          }
      }
    }

    def loopEvents(events: List[Event], state: State): State = {
      events match {
        case Nil => state
        case h :: t =>
          val st = loopEvent(h, state)
          st.event match {
            case None => loopEvents(t, st)
            case _    => st
          }
      }
    }

    def loop(state: State, from: EventNumber): Future[Option[SelectedSnapshot]] = {
      val req = ReadStreamEvents(eventStream(processorId), from, maxCount = 10, direction = Backward)

      connection.readBatch(req).flatMap {
        case Last(events) => Future.successful(loopEvents(events, state).event)
        case NotLast(events, from) =>
          val st = loopEvents(events, state)
          st.event match {
            case None => loop(st, from)
            case some => Future.successful(some)
          }
      }
    }

    loop(State.Empty, EventNumber.Last)
  }

  object SnapshotEvent1 {
    def unapply(event: Event): Option[SnapshotMetadata] = {
      serialization.deserialize(event.data.metadata.value.toArray, classOf[SnapshotMetadata]) match {
        case Success(x) => Some(x)
        case Failure(e) =>
          log.error(e, "Can't deserialize {}", event)
          None
      }
    }
  }

  def saveAsync(metadata: SnapshotMetadata, snapshot: Any) = {
    val streamId = eventStream(metadata.processorId)
    val event = EventData(
      eventType = "snapshot" /*TODO*/ ,
      data = Content(serialization.serialize(Snapshot(snapshot)).get),
      metadata = Content(serialization.serialize(metadata).get))
    connection.future(WriteEvents(streamId, List(event))).map(_ => Unit)
  }

  def saved(metadata: SnapshotMetadata) = {}

  def delete(metadata: SnapshotMetadata) = {
    write(metadata.processorId, Delete(metadata.sequenceNr, timestamp = metadata.timestamp))
  }

  def delete(processorId: String, criteria: SnapshotSelectionCriteria) = {
    write(processorId, SnapshotEvent.DeleteCriteria(
      maxSequenceNr = criteria.maxSequenceNr,
      maxTimestamp = criteria.maxTimestamp))
  }

  def eventStream(x: ProcessorId): EventStream.Id = EventStream(normalize(x) + "-snapshots")

  def write(processorId: String, sn: SnapshotEvent): Unit = {
    val streamId = eventStream(processorId)
    val event = EventData(
      eventType = eventType(sn.getClass) /*TODO*/ ,
      data = Content(serialization.serialize(sn).get))
    val f = connection.future(WriteEvents(streamId, List(event)))
    Await.result(f, deleteAwait)
  }
}

object EventStoreSnapshotStore {
  type Timestamp = Long

  sealed trait SnapshotEvent

  object SnapshotEvent {
    val clazz: Map[String, Class[_ <: SnapshotEvent]] = Map(
      "snapshot" -> classOf[Snapshot],
      "delete" -> classOf[Delete],
      "deleteCriteria" -> classOf[DeleteCriteria])

    val eventType: Map[Class[_ <: SnapshotEvent], String] = clazz.map(_.swap)

    @SerialVersionUID(0)
    case class Snapshot(data: Any) extends SnapshotEvent

    @SerialVersionUID(0)
    case class Delete(sequenceNr: SequenceNr, timestamp: Timestamp) extends SnapshotEvent

    @SerialVersionUID(0)
    case class DeleteCriteria(maxSequenceNr: SequenceNr, maxTimestamp: Timestamp) extends SnapshotEvent
  }

  case class State(
    event: Option[SelectedSnapshot],
    deleted: Set[SequenceNr],
    minSequenceNr: SequenceNr,
    minTimestamp: Timestamp)

  object State {
    val Empty: State = State(None, Set.empty, -1L, -1L)
  }
}