package akka.persistence.eventstore

import akka.serialization.Serializer
import eventstore.{ Event, EventData }

trait EventStoreSerializer extends Serializer {
  def toEvent(o: AnyRef): EventData
  def fromEvent(event: Event, manifest: Class[_]): AnyRef
}