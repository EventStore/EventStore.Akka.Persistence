package akka.persistence.eventstore

import akka.actor.{ ActorLogging, Actor }
import akka.serialization.{ SerializationExtension, Serialization }
import scala.concurrent.Future
import scala.util.control.NonFatal
import eventstore._

trait EventStorePlugin extends ActorLogging { self: Actor =>
  val connection: EsConnection = EventStoreExtension(context.system).connection
  val serialization: Serialization = SerializationExtension(context.system)
  import context.dispatcher

  def deserialize[T](event: Event, clazz: Class[T]): T = {
    val ser = serialization.serializerFor(clazz)
    val res = ser match {
      case ser: EventStoreSerializer => ser.fromEvent(event, clazz)
      case _                         => ser.fromBinary(event.data.data.value.toArray, clazz)
    }
    res.asInstanceOf[T]
  }

  def serialize(data: AnyRef, eventType: => Option[Any] = None): EventData = {
    val ser = serialization.findSerializerFor(data)
    ser match {
      case ser: EventStoreSerializer => ser.toEvent(data)
      case _ => EventData(
        eventType = (eventType getOrElse data).getClass.getName,
        data = Content(ser.toBinary(data)))
    }
  }

  def asyncUnit(x: => Future[_]): Future[Unit] = async(x).map(_ => Unit)

  def async[T](x: => Future[T]): Future[T] = try x catch {
    case NonFatal(f) => Future.failed(f)
  }

  def asyncSeq[A](x: => Iterable[Future[A]]): Future[Unit] = asyncUnit(Future.sequence(x))
}