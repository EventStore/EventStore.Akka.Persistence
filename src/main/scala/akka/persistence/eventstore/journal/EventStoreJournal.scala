package akka.persistence.eventstore.journal

import akka.persistence.journal.AsyncWriteJournal
import akka.persistence.{ PersistentConfirmation, PersistentId, PersistentRepr }
import akka.persistence.eventstore.Helpers._
import akka.actor.ActorLogging
import akka.serialization.{ Serialization, SerializationExtension }
import scala.collection.immutable.Seq
import scala.concurrent.Future
import eventstore._

class EventStoreJournal extends AsyncWriteJournal with ActorLogging {
  import EventStoreJournal._
  import EventStoreJournal.Update._
  import context.dispatcher

  val connection: EsConnection = EsConnection(context.system)
  val serialization: Serialization = SerializationExtension(context.system)

  def asyncWriteMessages(messages: Seq[PersistentRepr]) = futureSeq {
    messages.groupBy(_.processorId).map {
      case (processorId, msgs) =>
        val ds = msgs.map(toEventData)
        val expVer = msgs.head.sequenceNr - 1 match {
          case 0L => ExpectedVersion.NoStream
          case x  => ExpectedVersion(eventNumber(x))
        }
        val req = WriteEvents(eventStream(processorId), ds.toList, expVer)
        connection.future(req)
    }
  }

  def asyncWriteConfirmations(cs: Seq[PersistentConfirmation]) = futureSeq {
    cs.groupBy(_.processorId).map {
      case (processorId, cs) =>
        val map = cs.groupBy(_.sequenceNr).map {
          case (seqNr, pcs) => (seqNr, pcs.map(_.channelId))
        }
        addUpdate(processorId, Confirm(map))
    }
  }

  def asyncDeleteMessages(messageIds: Seq[PersistentId], permanent: Boolean) = futureSeq {
    messageIds.groupBy(_.processorId).map {
      case (processorId, ids) => addUpdate(processorId, Delete(ids.map(_.sequenceNr).toList, permanent))
    }
  }

  def asyncDeleteMessagesTo(processorId: ProcessorId, to: SequenceNr, permanent: Boolean) = {
    addUpdate(processorId, DeleteTo(to, permanent))
  }

  def asyncReadHighestSequenceNr(processorId: String, from: SequenceNr) = {
    val req = ReadEvent(eventStream(processorId), EventNumber.Last)
    connection.future(req).map {
      case ReadEventCompleted(event) => sequenceNumber(event.number)
    } recover {
      case StreamNotFound() => 0L
    }
  }

  def asyncReplayMessages(processorId: ProcessorId, from: SequenceNr, to: SequenceNr, max: Long)(replayCallback: (PersistentRepr) => Unit) = {
    def asyncReplayMessages(from: EventNumber.Exact, to: EventNumber.Exact, max: Int): Future[Unit] = {
      updates(processorId).flatMap {
        case Updates(confirms, d, p, dt, pt) =>
          def deleted(x: SequenceNr): Boolean = x <= dt || (d contains x)
          def deletedPermanently(x: SequenceNr): Boolean = x <= pt || (p contains x)

          val req = ReadStreamEvents(eventStream(processorId), from)
          connection.foldLeft(req, max) {
            case (left, event) if event.number <= to && left > 0 =>
              val seqNr = sequenceNumber(event.number)

              if (!deletedPermanently(seqNr)) {
                val repr = fromEventData(event.data).update(
                  deleted = deleted(seqNr),
                  confirms = confirms.getOrElse(seqNr, Seq.empty))
                replayCallback(repr)
              }

              left - 1
          }.map(_ => Unit)
      }
    }
    asyncReplayMessages(eventNumber(from), eventNumber(to), max.toIntOrError)
  }

  def eventStream(processorId: String): EventStream.Id = EventStream(normalize(processorId))

  def toEventData(x: PersistentRepr): EventData = EventData(
    eventType = x.payload.getClass.getSimpleName,
    data = Content(serialization.serialize(x).get))

  def fromEventData(x: EventData): PersistentRepr =
    serialization.deserialize(x.data.value.toArray, classOf[PersistentRepr]).get

  def updates(processorId: String): Future[Updates] = {
    def fold(updates: Updates, event: Event): Updates = {
      val eventType = event.data.eventType
      clazz.get(eventType) match {
        case None =>
          log.warning("Can't find class for eventType {}", eventType)
          updates

        case Some(c) => serialization.deserialize(event.data.data.value.toArray, c).get /*TODO*/ match {
          case Confirm(m1) =>
            val m2 = updates.confirms
            val confirms = m1 ++ m2.map { case (k, v) => k -> (v ++ m1.getOrElse(k, Nil)) }
            updates.copy(confirms = confirms)

          case Delete(sequenceNrs, permanent) =>
            if (!permanent) updates.copy(deleted = updates.deleted ++ sequenceNrs)
            else updates.copy(deletedPermanently = updates.deletedPermanently ++ sequenceNrs)

          case DeleteTo(toSequenceNr, permanent) =>
            if (!permanent) updates.copy(deletedTo = math.max(toSequenceNr, updates.deletedTo))
            else updates.copy(deletedPermanentlyTo = math.max(toSequenceNr, updates.deletedPermanentlyTo))
        }
      }
    }

    val req = ReadStreamEvents(Update.eventStream(processorId))
    connection.foldLeft(req, Updates.Empty) {
      case (updates, event) => fold(updates, event)
    }
  }

  def addUpdate(processorId: String, x: Update): Future[Unit] = {
    val streamId = Update.eventStream(processorId)
    val eventType = Update.eventType(x.getClass)
    val req = WriteEvents(streamId, List(EventData(eventType, data = Content(serialization.serialize(x).get))))
    connection.future(req).map(_ => Unit)
  }

  def futureSeq[A](in: Iterable[Future[A]]): Future[Unit] = {
    Future.sequence(in).map(_ => Unit)
  }
}

object EventStoreJournal {
  sealed trait Update

  object Update {
    def eventStream(x: String): EventStream.Id = EventStream(normalize(x) + "-updates")

    val clazz: Map[String, Class[_ <: Update]] = Map(
      "confirm" -> classOf[Confirm],
      "delete" -> classOf[Delete],
      "deleteTo" -> classOf[DeleteTo])

    val eventType: Map[Class[_ <: Update], String] = clazz.map(_.swap)

    @SerialVersionUID(0)
    case class Confirm(confirms: Confirms) extends Update

    @SerialVersionUID(0)
    case class Delete(sequenceNrs: Seq[SequenceNr], permanent: Boolean) extends Update

    @SerialVersionUID(0)
    case class DeleteTo(toSequenceNr: SequenceNr, permanent: Boolean) extends Update
  }

  case class Updates(
    confirms: Confirms,
    deleted: Set[SequenceNr],
    deletedPermanently: Set[SequenceNr],
    deletedTo: SequenceNr,
    deletedPermanentlyTo: SequenceNr)

  object Updates {
    val Empty: Updates = Updates(Map.empty, Set.empty, Set.empty, -1L, -1L)
  }
}