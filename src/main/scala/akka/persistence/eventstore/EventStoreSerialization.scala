package akka.persistence.eventstore

import scala.reflect.ClassTag
import akka.actor.ActorSystem
import akka.serialization.{ Serialization, SerializationExtension }
import eventstore.core.util.uuid.randomUuid
import eventstore.{ Content, Event, EventData}

case class EventStoreSerialization(serialization: Serialization) {
  def deserialize[T](event: Event)(implicit tag: ClassTag[T]): T = {
    val ser = serialization.serializerFor(tag.runtimeClass)
    val res = ser match {
      case ser: EventStoreSerializer => ser.fromEvent(event, tag.runtimeClass)
      case _                         => ser.fromBinary(event.data.data.value.toArray, tag.runtimeClass)
    }
    res.asInstanceOf[T]
  }

  def serialize(data: AnyRef, eventType: => Option[Any] = None): EventData = {
    val ser = serialization.findSerializerFor(data)
    ser match {
      case ser: EventStoreSerializer => ser.toEvent(data)
      case _ => EventData(
        eventType = (eventType getOrElse data).getClass.getName,
        eventId = randomUuid,
        data = Content(ser.toBinary(data))
      )
    }
  }
}

object EventStoreSerialization {
  def apply(system: ActorSystem): EventStoreSerialization = {
    EventStoreSerialization(SerializationExtension(system))
  }
}