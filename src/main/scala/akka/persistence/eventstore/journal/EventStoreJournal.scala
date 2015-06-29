package akka.persistence.eventstore.journal

import java.util.concurrent.ConcurrentHashMap
import akka.persistence.eventstore.Helpers._
import akka.persistence.eventstore.{ EventStorePlugin, UrlEncoder }
import akka.persistence.journal.AsyncWriteJournal
import akka.persistence.{ AtomicWrite, PersistentRepr }
import eventstore._
import org.json4s.DefaultFormats
import org.json4s.native.JsonMethods.parse
import scala.collection.immutable.Seq
import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }

class EventStoreJournal extends AsyncWriteJournal with EventStorePlugin {
  import EventStoreJournal._
  import context.dispatcher

  val deleteToCache = new DeleteToCache()

  def config = context.system.settings.config.getConfig("eventstore.persistence.journal")

  override def asyncWriteMessages(messages: Seq[AtomicWrite]): Future[Seq[Try[Unit]]] = {

    def write(persistenceId: PersistenceId, messages: Seq[PersistentRepr]): Future[Try[Unit]] = {
      val expVer = messages.head.sequenceNr - 1 match {
        case 0L => ExpectedVersion.NoStream
        case x  => ExpectedVersion.Exact(eventNumber(x))
      }
      Try(messages.map(x => serialize(x, Some(x.payload)))) match {
        case Success(events) =>
          connection.future(WriteEvents(eventStream(persistenceId), events.toList, expVer)).map(_ => Success(()))
        case Failure(ex) =>
          Future.successful(Failure(ex))
      }
    }

    val msgMap = messages.map(m => (m.payload.head.persistenceId, m.payload)).toMap
    Future.traverse(msgMap) {
      case (persistenceId, msgs) => write(persistenceId, msgs)
    }.mapTo[Seq[Try[Unit]]]
  }

  def asyncDeleteMessagesTo(persistenceId: PersistenceId, to: SequenceNr) = asyncUnit {
    val json = deleteToCache.get(persistenceId).fold(s"""{"$TruncateBefore":$to}""") {
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
      case _: StreamNotFoundException => 0L
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

  def eventStream(x: PersistenceId): EventStream.Plain = EventStream(prefix + UrlEncoder(x)) match {
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
          .recover { case _: StreamNotFoundException => -1L }
          .map { x => deleteToCache.add(persistenceId, x); x }
    }
  }
}

object EventStoreJournal {
  val TruncateBefore = "$tb"
  val DeleteTo = "ap-deleteTo"

  class DeleteToCache {
    private val map = new ConcurrentHashMap[PersistenceId, SequenceNr]()

    def get(persistenceId: PersistenceId): Option[SequenceNr] = {
      Option(map.get(persistenceId))
    }

    def add(persistenceId: PersistenceId, sequenceNr: SequenceNr): Unit = {
      map.put(persistenceId, sequenceNr)
    }
  }
}