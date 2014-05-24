package akka.persistence.eventstore.journal

import akka.persistence.journal.AsyncWriteJournal
import akka.persistence.{ PersistentConfirmation, PersistentId, PersistentRepr }
import akka.serialization.{ Serialization, SerializationExtension }
import scala.PartialFunction.cond
import scala.collection.immutable.Seq
import scala.concurrent.{ ExecutionContext, Future }
import eventstore._
import eventstore.ReadDirection.Forward

class EventStoreJournal extends AsyncWriteJournal {
  import EventStoreJournal._
  import EventStoreJournal.Update._
  import context.dispatcher

  protected val connection: EsConnection = EsConnection(context.system)
  private val serialization: Serialization = SerializationExtension(context.system)

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

  def asyncDeleteMessagesTo(processorId: String, to: SequenceNr, permanent: Boolean) = {
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

  def asyncReplayMessages(processorId: String, from: SequenceNr, to: SequenceNr, max: Long)(replayCallback: (PersistentRepr) => Unit) = {
    def asyncReplayMessages(from: EventNumber.Exact, to: EventNumber.Exact, max: Int): Future[Unit] = {
      updates(processorId).flatMap {
        case Updates(confirms, d, p, dt, pt) =>
          def deleted(x: SequenceNr): Boolean = x <= dt || (d contains x)
          def deletedPermanently(x: SequenceNr): Boolean = x <= pt || (p contains x)

          connection.readWhile(eventStream(processorId), max, from) { (left, event) =>
            if (event.number > to || left <= 0) None
            else {
              val seqNr = sequenceNumber(event.number)

              if (!deletedPermanently(seqNr)) {
                val repr = fromEventData(event.data).update(
                  deleted = deleted(seqNr),
                  confirms = confirms.getOrElse(seqNr, Seq.empty))
                replayCallback(repr)
              }

              left - 1 match {
                case 0 => None
                case x => Some(x)
              }
            }
          }.map(_ => Unit)
      }
    }
    asyncReplayMessages(eventNumber(from), eventNumber(to), asInt(max))
  }

  def eventStream(processorId: String): EventStream.Id = {
    EventStream(processorId.split('/').filter(_.nonEmpty).mkString("-"))
  }

  private def eventNumber(x: SequenceNr): EventNumber.Exact = EventNumber(asInt(x) - 1)

  private def sequenceNumber(x: EventNumber.Exact): SequenceNr = x.value.toLong + 1

  def toEventData(x: PersistentRepr): EventData = EventData(
    eventType = x.payload.getClass.getSimpleName,
    data = Content(serialization.serialize(x).get))

  def fromEventData(x: EventData): PersistentRepr =
    serialization.deserialize(x.data.value.toArray, classOf[PersistentRepr]).get

  def asInt(x: Long): Int = {
    if (x == Long.MaxValue) Int.MaxValue
    else {
      if (x.isValidInt) x.toInt
      else throw new RuntimeException(s"Can't convert $x to Int")
    }
  }

  def updates(processorId: String): Future[Updates] = {
    connection.readWhile(Update.eventStream(processorId), Updates.Empty) {
      (metadata, event) =>
        Some(clazz.get(event.data.eventType).fold(metadata) { clazz =>
          serialization.deserialize(event.data.data.value.toArray, clazz).get match {
            case Confirm(m1) =>
              val m2 = metadata.confirms
              val confirms = m1 ++ m2.map { case (k, v) => k -> (v ++ m1.getOrElse(k, Nil)) }
              metadata.copy(confirms = confirms)

            case Delete(sequenceNrs, permanent) =>
              if (!permanent) metadata.copy(deleted = metadata.deleted ++ sequenceNrs)
              else metadata.copy(deletedPermanently = metadata.deletedPermanently ++ sequenceNrs)

            case DeleteTo(toSequenceNr, permanent) =>
              if (!permanent) metadata.copy(deletedTo = math.max(toSequenceNr, metadata.deletedTo))
              else metadata.copy(deletedPermanentlyTo = math.max(toSequenceNr, metadata.deletedPermanentlyTo))
          }
        })
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
  type SequenceNr = Long
  type ChannelId = String
  type Confirms = Map[SequenceNr, Seq[ChannelId]]

  case class Updates(
    confirms: Confirms,
    deleted: Set[SequenceNr],
    deletedPermanently: Set[SequenceNr],
    deletedTo: SequenceNr,
    deletedPermanentlyTo: SequenceNr)

  object Updates {
    val Empty: Updates = Updates(Map.empty, Set.empty, Set.empty, -1L, -1L)
  }

  sealed trait Batch {
    def events: List[Event]
  }

  object Batch {
    val Empty: Batch = Last(Nil)

    def apply(x: ReadStreamEventsCompleted): Batch =
      if (x.endOfStream) Last(x.events)
      else NotLast(x.events, x.nextEventNumber)

    case class Last(events: List[Event]) extends Batch
    case class NotLast(events: List[Event], next: EventNumber) extends Batch
  }

  sealed trait Update

  object Update {
    def eventStream(x: String): EventStream.Id = EventStream(s"$x-updates")

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

  object StreamNotFound {
    def unapply(x: Throwable): Boolean = cond(x) {
      case EsException(EsError.StreamNotFound, _) => true
    }
  }

  implicit class RichConnection(val self: EsConnection) extends AnyVal {
    def readWhile[T](streamId: EventStream.Id, default: T, from: EventNumber = EventNumber.First, direction: ReadDirection = Forward)(process: (T, Event) => Option[T])(implicit ex: ExecutionContext): Future[T] = {
      import Batch._

      def loop(events: List[Event], t: T, quit: T => Future[T]): Future[T] = events match {
        case Nil     => quit(t)
        case x :: xs => process(t, x).fold(Future.successful(t))(loop(xs, _, quit))
      }

      def readWhile(from: EventNumber, t: T): Future[T] = readBatch(ReadStreamEvents(streamId, from)).flatMap {
        case Last(events)          => loop(events, t, Future.successful)
        case NotLast(events, from) => loop(events, t, readWhile(from, _))
      }

      readWhile(from, default)
    }

    def readBatch(req: ReadStreamEvents)(implicit ex: ExecutionContext): Future[Batch] = {
      self.future(req).map(Batch(_)).recover {
        case StreamNotFound() => Batch.Empty
      }
    }
  }
}