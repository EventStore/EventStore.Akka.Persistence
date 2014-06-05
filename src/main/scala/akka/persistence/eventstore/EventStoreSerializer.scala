package akka.persistence.eventstore

import akka.serialization.Serializer
import eventstore.ContentType

trait EventStoreSerializer extends Serializer {
  def contentType: ContentType
}