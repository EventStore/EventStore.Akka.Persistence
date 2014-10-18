package akka.persistence.eventstore.journal

import akka.persistence.journal.AsyncWriteJournal
import akka.persistence.{ PersistentConfirmation, PersistentRepr }
import akka.persistence.eventstore.Helpers._
import akka.persistence.eventstore.{ UrlEncoder, EventStorePlugin }
import scala.collection.immutable.Seq
import scala.concurrent.Future
import eventstore._
import org.json4s.DefaultFormats
import org.json4s.native.JsonMethods.parse

class EventStoreJournal extends AsyncWriteJournal with EventStorePlugin {
  import EventStoreJournal._
  import context.dispatcher

  val deleteToCache = new DeleteToCache()

  def asyncWriteMessages(messages: Seq[PersistentRepr]) = asyncSeq {
    messages.groupBy(_.persistenceId).map {
      case (persistenceId, msgs) =>
        val events = msgs.map(x => serialize(x, Some(x.payload)))
        val expVer = msgs.head.sequenceNr - 1 match {
          case 0L => ExpectedVersion.NoStream
          case x  => ExpectedVersion.Exact(eventNumber(x))
        }
        val req = WriteEvents(eventStream(persistenceId), events.toList, expVer)
        connection.future(req)
    }
  }

  def asyncDeleteMessagesTo(persistenceId: PersistenceId, to: SequenceNr, permanent: Boolean) = asyncUnit {
    val json =
      if (!permanent) s"""{"$DeleteTo":$to}"""
      else deleteToCache.get(persistenceId).fold(s"""{"$TruncateBefore":$to}""") {
        deleteTo => s"""{"$TruncateBefore":$to,"$DeleteTo":$deleteTo}"""
      }
    val eventData = EventData.StreamMetadata(Content.Json(json))
    val streamId = eventStream(persistenceId).metadata
    val req = WriteEvents(streamId, List(eventData))
    connection.future(req)
  }

  def asyncReadHighestSequenceNr(persistenceId: PersistenceId, from: SequenceNr) = async {
    val req = ReadEvent(eventStream(persistenceId), EventNumber.Last)
    connection.future(req).map {
      case ReadEventCompleted(event) => sequenceNumber(event.number)
    } recover {
      case StreamNotFound() => 0L
    }
  }

  def asyncReplayMessages(persistenceId: PersistenceId, from: SequenceNr, to: SequenceNr, max: Long)(replayCallback: (PersistentRepr) => Unit) = asyncUnit {
    def asyncReplayMessages(from: EventNumber.Exact, to: EventNumber.Exact, max: Int) = {
      deletedTo(persistenceId).flatMap { deletedTo =>
        val req = ReadStreamEvents(eventStream(persistenceId), from)
        connection.foldLeft(req, max) {
          case (left, event) if event.number <= to && left > 0 =>
            val seqNr = sequenceNumber(event.number)
            val repr = deserialize(event, classOf[PersistentRepr]).update(deleted = seqNr <= deletedTo)
            replayCallback(repr)
            left - 1
        }
      }
    }
    asyncReplayMessages(eventNumber(from), eventNumber(to), max.toIntOrError)
  }

  def asyncWriteConfirmations(cs: Seq[PersistentConfirmation]) =
    sys.error("asyncWriteConfirmations is deprecated and not supported")

  def asyncDeleteMessages(messageIds: Seq[akka.persistence.PersistentId], permanent: Boolean) =
    sys.error("asyncDeleteMessages is deprecated and not supported")

  def eventStream(x: PersistenceId): EventStream.Plain = EventStream(UrlEncoder(x)) match {
    case plain: EventStream.Plain => plain
    case other                    => sys.error(s"Cannot create plain event stream for $x")
  }

  def deletedTo(persistenceId: PersistenceId): Future[SequenceNr] = {
    deleteToCache.get(persistenceId) match {
      case Some(x) => Future.successful(x)
      case None =>
        val req = ReadEvent(eventStream(persistenceId).metadata, EventNumber.Last)
        val future = connection.future(req).map {
          case ReadEventCompleted(x) =>
            val json = parse(x.data.data.value.utf8String)
            implicit val formats = DefaultFormats
            (json \ DeleteTo).extract[Option[SequenceNr]] getOrElse -1L
        }
        future
          .recover { case StreamNotFound() => -1L }
          .map { x => deleteToCache.add(persistenceId, x); x }
    }
  }
}

object EventStoreJournal {
  val TruncateBefore = "$tb"
  val DeleteTo = "ap-deleteTo"

  class DeleteToCache {
    private var map = Map[PersistenceId, SequenceNr]()
    def get(persistenceId: PersistenceId): Option[SequenceNr] = map.get(persistenceId)

    def add(persistenceId: PersistenceId, sequenceNr: SequenceNr): Unit = synchronized {
      map = map + (persistenceId -> sequenceNr)
    }
  }
}