package akka.persistence.eventstore

import akka.actor.{ Actor, ActorLogging }
import akka.serialization.{ Serialization, SerializationExtension }
import com.typesafe.config.Config
import eventstore._
import scala.concurrent.Future
import scala.util.control.NonFatal

trait EventStorePlugin extends ActorLogging { self: Actor =>
  val connection: EsConnection = EventStoreExtension(context.system).connection
  val serialization: Serialization = SerializationExtension(context.system)
  val prefix: String = config.getString("stream-prefix")
  import context.dispatcher

  def config: Config

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

  def asyncUnit(x: => Future[_]): Future[Unit] = async(x).map[Unit](_ => ())

  def async[T](x: => Future[T]): Future[T] = try x catch {
    case NonFatal(f) => Future.failed(f)
  }
}