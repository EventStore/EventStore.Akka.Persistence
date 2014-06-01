package akka.persistence.eventstore

import akka.actor.Actor
import akka.serialization.{ SerializationExtension, Serialization }
import eventstore.{ Content, EsConnection }

trait EventStorePlugin { self: Actor =>
  val connection: EsConnection = EsConnection(context.system)
  private val serialization: Serialization = SerializationExtension(context.system)

  // TODO check usages and tries
  def deserialize[T](content: Content, clazz: Class[T]): T = {
    serialization.deserialize(content.value.toArray, clazz).get
  }

  // TODO check usages and tries
  def serialize(x: AnyRef): Content = {
    Content(serialization.serialize(x).get)
  }
}
