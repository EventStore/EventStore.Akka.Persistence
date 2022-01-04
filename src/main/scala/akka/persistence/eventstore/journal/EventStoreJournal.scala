package akka.persistence.eventstore.journal

import scala.annotation.tailrec
import scala.collection.immutable.Seq
import scala.concurrent.Future
import akka.persistence.eventstore.EventStorePlugin
import akka.persistence.eventstore.Helpers._
import akka.persistence.journal.AsyncWriteJournal
import akka.persistence.{AtomicWrite, PersistentRepr}
import com.typesafe.config.Config
import io.circe._
import eventstore.core.util.uuid.randomUuid
import eventstore.{ReadStreamEvents, _}


class EventStoreJournal extends AsyncWriteJournal with EventStorePlugin {
  import EventStoreJournal._
  import context.dispatcher

  def config: Config = context.system.settings.config.getConfig("eventstore.persistence.journal")

  protected lazy val writeBatchSize: Int = config.getInt("write-batch-size")
  protected lazy val readBatchSize: Int = config.getInt("read-batch-size")

  def asyncWriteMessages(messages: Seq[AtomicWrite]): Future[Nil.type] = {

    if (messages.isEmpty) Future successful Nil
    else Future {
      val atomicWrite = messages.head
      val persistenceId = atomicWrite.persistenceId
      val seqNr = atomicWrite.lowestSequenceNr
      val events = for {
        atomicWrite <- messages
        persistentRepr <- atomicWrite.payload
      } yield serialization.serialize(persistentRepr, Some(persistentRepr.payload))

      def writeEvents(events: Seq[EventData], seqNr: Long): Future[Nil.type] = {
        if (events.isEmpty) Future successful Nil
        else {
          val expVer = {
            val expVer = seqNr - 1
            if (expVer == 0L) ExpectedVersion.NoStream else ExpectedVersion.Exact(eventNumber(expVer))
          }
          val req = WriteEvents(eventStream(persistenceId), events.toList, expVer)
          for { _ <- connection(req) } yield Nil
        }
      }

      @tailrec def loop(
        future:  Future[Nil.type],
        batches: List[Seq[EventData]],
        seqNr:   Long
      ): Future[Nil.type] = {

        if (batches.isEmpty) future
        else {
          val events = batches.head
          val result = for {
            _ <- future
            result <- writeEvents(events, seqNr)
          } yield result
          loop(result, batches.tail, seqNr + events.size)
        }
      }

      if (events.size <= writeBatchSize) {
        writeEvents(events, seqNr)
      } else {
        val batches = events.grouped(writeBatchSize).toList
        loop(Future successful Nil, batches, seqNr)
      }
    } flatMap { identity }
  }

  def asyncDeleteMessagesTo(persistenceId: PersistenceId, to: SequenceNr): Future[Unit] = {

    def delete(to: SequenceNr): Future[Unit] = asyncUnit {
      val json = Json.obj(TruncateBefore -> Json.fromLong(to))
      val eventData = EventData.StreamMetadata(Content.Json(json.noSpaces), randomUuid)
      val streamId = eventStream(persistenceId).metadata
      val req = WriteEvents(streamId, List(eventData))
      connection(req)
    }

    if (to != Long.MaxValue) delete(to)
    else for {
      to <- asyncReadHighestSequenceNr(persistenceId, 0L)
      _ <- delete(to)
    } yield ()
  }

  def asyncReadHighestSequenceNr(persistenceId: PersistenceId, from: SequenceNr): Future[SequenceNr] = async {
    val stream = eventStream(persistenceId)
    val req = ReadEvent(stream, EventNumber.Last)
    connection(req) map {
      case ReadEventCompleted(event) => sequenceNumber(event.number)
    } recoverWith {
      case _: StreamNotFoundException => Future successful 0L
      case eventNotFound: EventNotFoundException =>
        connection.getStreamMetadata(stream) map { md =>
          parser.parse(md.value.utf8String).flatMap(_.hcursor.get[Long](TruncateBefore)).fold(throw _, identity)
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
  )(recoveryCallback: PersistentRepr => Unit): Future[Unit] = {

    import context.system

    def replayMany(from: Option[EventNumber.Exact], to: EventNumber.Exact): Future[Unit] = Future {
      val streamId = eventStream(persistenceId)
      connection.streamSource(streamId, from, infinite = false, readBatchSize = readBatchSize)
        .takeWhile { event => event.number <= to }
        .take(max)
        .runForeach { event => recoveryCallback(serialization.deserialize[PersistentRepr](event)) }
        .map { _ => () }
    } flatMap { identity }

    def replayFew(maxCount: Long): Future[Unit] = {
      val streamId = eventStream(persistenceId)
      val readStreamEvents = ReadStreamEvents(
        streamId,
        if (from <= 1) EventNumber.First else eventNumber(from),
        maxCount = maxCount.toInt,
        resolveLinkTos = settings.resolveLinkTos,
        requireMaster = settings.requireMaster
      )

      for {
        result <- connection(readStreamEvents)
      } yield {
        for { event <- result.events } recoveryCallback(serialization.deserialize[PersistentRepr](event))
        ()
      }
    }

    val maxCount = (to - from + 1) min max

    if (to <= 0L) Future(())
    else if (maxCount <= 0) Future(())
    else if (maxCount <= readBatchSize) replayFew(maxCount)
    else replayMany(if (from <= 1) None else Some(eventNumber(from - 1)), eventNumber(to))
  }

  def eventStream(x: PersistenceId): EventStream.Id = EventStream(prefix + x) match {
    case id: EventStream.Id => id
    case _                  => sys.error(s"Cannot create EventStream.Id for $x")
  }
}

object EventStoreJournal {
  val TruncateBefore: String = "$tb"
}