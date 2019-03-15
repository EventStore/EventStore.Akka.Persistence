package akka.persistence.eventstore

import eventstore._
import eventstore.akka.EsConnection
import scala.annotation.tailrec
import scala.concurrent.{ ExecutionContext, Future }

object Helpers {
  type Timestamp = Long
  type PersistenceId = String
  type SequenceNr = Long

  def eventNumber(x: SequenceNr): EventNumber.Exact = EventNumber.Exact(x - 1)

  def sequenceNumber(x: EventNumber.Exact): SequenceNr = x.value + 1

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

  implicit class RichConnection(val self: EsConnection) extends AnyVal {
    def foldLeft[T](req: ReadStreamEvents, default: T)(pf: PartialFunction[(T, Event), T])(implicit ex: ExecutionContext): Future[T] = {

      import Batch._

      def readBatch(req: ReadStreamEvents): Future[Batch] = {
        self(req) map { Batch.apply } recover { case _: StreamNotFoundException => Batch.Empty }
      }

      @tailrec def loop(events: List[Event], t: T, quit: T => Future[T]): Future[T] = events match {
        case Nil => quit(t)
        case x :: xs => pf.lift(t -> x) match {
          case None    => Future successful t
          case Some(x) => loop(xs, x, quit)
        }
      }

      def foldLeft(from: EventNumber, t: T): Future[T] = {
        readBatch(req.copy(fromNumber = from)).flatMap {
          case Last(events)          => loop(events, t, Future.successful)
          case NotLast(events, from) => loop(events, t, foldLeft(from, _))
        }
      }

      foldLeft(req.fromNumber, default)
    }
  }
}