package akka.persistence.eventstore.journal

import akka.persistence.eventstore.EventStorePlugin
import akka.persistence.eventstore.Helpers._
import akka.persistence.journal.AsyncWriteJournal
import akka.persistence.{ AtomicWrite, PersistentRepr }
import eventstore._
import play.api.libs.json._

import scala.annotation.tailrec
import scala.collection.immutable.Seq
import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }

class EventStoreJournal extends AsyncWriteJournal with EventStorePlugin {
  import EventStoreJournal._
  import context.dispatcher

  def config = context.system.settings.config.getConfig("eventstore.persistence.journal")

  protected lazy val writeBatchSize = config.getInt("write-batch-size")

  def asyncWriteMessages(messages: Seq[AtomicWrite]) = {

    @tailrec def writeAtomicWrites(
      messages: Iterator[AtomicWrite],
      progress: Map[String, Future[Try[Unit]]],
      results:  List[Future[Try[Unit]]]
    ): Seq[Future[Try[Unit]]] = {

      if (messages.isEmpty) results.reverse
      else {
        val message = messages.next()
        val payload = message.payload

        val persistenceId = message.persistenceId
        def write: Future[Try[Unit]] = {
          Try {
            payload.map(x => serialization.serialize(x, Some(x.payload)))
          } map { events =>

            @tailrec def writePayloads(
              events:     Iterator[Seq[EventData]],
              previous:   Future[Try[Unit]],
              sequenceNr: Long
            ): Future[Try[Unit]] = {

              if (events.isEmpty) previous
              else {
                val batch = events.next()

                def write = {
                  val expVer = {
                    val expVer = sequenceNr - 1
                    if (expVer == 0L) ExpectedVersion.NoStream else ExpectedVersion.Exact(eventNumber(expVer))
                  }
                  val req = WriteEvents(eventStream(persistenceId), batch.toList, expVer)
                  (connection future req) map { _ => Success(()) } recover { case e => Failure(e) }
                }

                val result = for {
                  previous <- previous
                  result <- if (previous.isFailure) Future successful previous else write
                } yield result

                writePayloads(events, result, sequenceNr + batch.size)
              }
            }

            writePayloads(events grouped writeBatchSize, Future successful Success(()), payload.head.sequenceNr)
          } recover {
            case e => Future successful Failure(e)
          }
        }.get

        val result = for {
          previous <- progress.getOrElse(persistenceId, Future successful Success(()))
          result <- if (previous.isFailure) Future successful previous else write
        } yield result

        writeAtomicWrites(messages, progress + (persistenceId -> result), result :: results)
      }
    }

    val futures = writeAtomicWrites(messages.toIterator, Map(), Nil)
    Future sequence futures
  }

  def asyncDeleteMessagesTo(persistenceId: PersistenceId, to: SequenceNr) = {

    def delete(to: SequenceNr) = asyncUnit {
      val json = Json.obj(TruncateBefore -> to.toIntOrError)
      val eventData = EventData.StreamMetadata(Content.Json(json.toString()))
      val streamId = eventStream(persistenceId).metadata
      val req = WriteEvents(streamId, List(eventData))
      connection future req
    }

    if (to != Long.MaxValue) delete(to)
    else for {
      to <- asyncReadHighestSequenceNr(persistenceId, 0)
      _ <- delete(to)
    } yield ()
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
    from:          SequenceNr,
    to:            SequenceNr,
    max:           Long
  )(recoveryCallback: (PersistentRepr) => Unit) = asyncUnit {

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
    else asyncReplayMessages(eventNumber(from max 1), eventNumber(to), max.toIntOrError)
  }

  def eventStream(x: PersistenceId): EventStream.Id = EventStream(prefix + x) match {
    case id: EventStream.Id => id
    case other              => sys.error(s"Cannot create id event stream for $x")
  }
}

object EventStoreJournal {
  val TruncateBefore: String = "$tb"
}