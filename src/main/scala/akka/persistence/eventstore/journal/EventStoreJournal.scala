package akka.persistence.eventstore.journal

import akka.persistence.eventstore.EventStorePlugin
import akka.persistence.eventstore.Helpers._
import akka.persistence.journal.AsyncWriteJournal
import akka.persistence.{ AtomicWrite, PersistentRepr }
import eventstore._
import play.api.libs.json._

import scala.collection.immutable.Seq
import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }

class EventStoreJournal extends AsyncWriteJournal with EventStorePlugin {
  import EventStoreJournal._
  import context.dispatcher

  def config = context.system.settings.config.getConfig("eventstore.persistence.journal")

  def asyncWriteMessages(messages: Seq[AtomicWrite]) = {
    def write(persistenceId: PersistenceId, messages: Seq[PersistentRepr]): Future[Try[Unit]] = {
      val events = messages.map(x => serialization.serialize(x, Some(x.payload)))
      val expVer = messages.head.sequenceNr - 1 match {
        case 0L => ExpectedVersion.NoStream
        case x  => ExpectedVersion.Exact(eventNumber(x))
      }
      val req = WriteEvents(eventStream(persistenceId), events.toList, expVer)
      (connection future req)
        .map { _ => Success(()) }
        .recover { case e => Failure(e) }
    }

    Future.traverse(messages) { x => write(x.persistenceId, x.payload) }
  }

  def asyncDeleteMessagesTo(persistenceId: PersistenceId, to: SequenceNr) = asyncUnit {
    val json = Json.obj(TruncateBefore -> to)
    val eventData = EventData.StreamMetadata(Content.Json(json.toString()))
    val streamId = eventStream(persistenceId).metadata
    val req = WriteEvents(streamId, List(eventData))
    connection future req
  }

  def asyncReadHighestSequenceNr(persistenceId: PersistenceId, from: SequenceNr) = async {
    val stream = eventStream(persistenceId)
    val req = ReadEvent(eventStream(persistenceId), EventNumber.Last)
    (connection future req).map {
      case ReadEventCompleted(event) => sequenceNumber(event.number)
    } recoverWith {
      case _: StreamNotFoundException => Future successful 0L
      case eventNotFound: EventNotFoundException =>
        connection.getStreamMetadata(stream) map { metadata =>
          val str = metadata.value.utf8String
          val tb = for {
            obj <- (Json parse str).validate[JsObject]
            tb <- obj.value.getOrElse(TruncateBefore, JsNull).validate[Double]
          } yield tb.toLong

          tb recoverTotal { error => sys error s"$persistenceId: $error" }
        } recover {
          case e => throw e initCause eventNotFound
        }
    }
  }

  def asyncReplayMessages(
    persistenceId: PersistenceId,
    from: SequenceNr,
    to: SequenceNr,
    max: Long)(recoveryCallback: (PersistentRepr) => Unit) = asyncUnit {

    def asyncReplayMessages(from: EventNumber.Exact, to: EventNumber.Exact, max: Int) = {
      val req = ReadStreamEvents(eventStream(persistenceId), from)
      connection.foldLeft(req, max) {
        case (left, event) if event.number <= to && left > 0 =>
          val repr = serialization.deserialize[PersistentRepr](event)
          recoveryCallback(repr)
          left - 1
      }
    }

    if (to == 0L) Future(())
    else asyncReplayMessages(eventNumber(from), eventNumber(to), max.toIntOrError)
  }

  def eventStream(x: PersistenceId): EventStream.Id = EventStream(prefix + x) match {
    case id: EventStream.Id => id
    case other              => sys.error(s"Cannot create id event stream for $x")
  }
}

object EventStoreJournal {
  val TruncateBefore: String = "$tb"
}