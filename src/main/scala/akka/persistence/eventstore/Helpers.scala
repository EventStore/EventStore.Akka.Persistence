package akka.persistence.eventstore

import scala.concurrent.{ ExecutionContext, Future }
import scala.PartialFunction.cond
import eventstore._
import eventstore.ReadStreamEventsCompleted

object Helpers {
  type Timestamp = Long
  type PersistenceId = String
  type SequenceNr = Long

  def eventNumber(x: SequenceNr): EventNumber.Exact = EventNumber.Exact(x.toIntOrError - 1)

  def sequenceNumber(x: EventNumber.Exact): SequenceNr = x.value.toLong + 1

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

  object StreamNotFound {
    def unapply(x: Throwable): Boolean = cond(x) {
      case EsException(EsError.StreamNotFound, _) => true
    }
  }

  implicit class RichLong(val self: Long) extends AnyVal {
    def toIntOrError: Int =
      if (self == Long.MaxValue) Int.MaxValue
      else {
        if (self.isValidInt) self.toInt
        else sys.error(s"Cannot convert $self to Int")
      }
  }

  implicit class RichConnection(val self: EsConnection) extends AnyVal {
    def foldLeft[T](req: ReadStreamEvents, default: T)(pf: PartialFunction[(T, Event), T])(implicit ex: ExecutionContext): Future[T] = {

      import Batch._

      def readBatch(req: ReadStreamEvents): Future[Batch] = {
        self.future(req).map(Batch(_)).recover {
          case StreamNotFound() => Batch.Empty
        }
      }

      def loop(events: List[Event], t: T, quit: T => Future[T]): Future[T] = events match {
        case Nil     => quit(t)
        case x :: xs => pf.lift(t, x).fold(Future.successful(t))(loop(xs, _, quit))
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