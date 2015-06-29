package akka.persistence.eventstore

import akka.actor.{ ActorRef, ExtendedActorSystem }
import akka.persistence.{ PersistentRepr, SnapshotMetadata }
import akka.persistence.eventstore.snapshot.EventStoreSnapshotStore.SnapshotEvent
import akka.persistence.eventstore.snapshot.EventStoreSnapshotStore.SnapshotEvent.Snapshot
import akka.util.ByteString
import org.json4s._
import org.json4s.Extraction.decompose
import org.json4s.native.Serialization.{ read, write }
import java.nio.charset.Charset
import java.nio.ByteBuffer
import eventstore.{ Content, EventData, Event, ContentType }

class Json4sSerializer(val system: ExtendedActorSystem) extends EventStoreSerializer {
  import Json4sSerializer._

  implicit val formats = DefaultFormats + SnapshotSerializer + PersistentReprSerializer + ActorRefSerializer

  def identifier = Identifier

  def includeManifest = true

  def fromBinary(bytes: Array[Byte], manifestOpt: Option[Class[_]]) = {
    implicit val manifest = manifestOpt match {
      case Some(x) => Manifest.classType(x)
      case None    => Manifest.AnyRef
    }
    read(new String(bytes, UTF8))
  }

  def toBinary(o: AnyRef) = write(o).getBytes(UTF8)

  def toEvent(x: AnyRef) = x match {
    case x: PersistentRepr => EventData(
      eventType = classFor(x).getName,
      data = Content(ByteString(toBinary(x)), ContentType.Json))

    case x: SnapshotEvent => EventData(
      eventType = classFor(x).getName,
      data = Content(ByteString(toBinary(x)), ContentType.Json))

    case _ => sys.error(s"Cannot serialize $x, SnapshotEvent expected")
  }

  def fromEvent(event: Event, manifest: Class[_]) = {
    val clazz = Class.forName(event.data.eventType)
    val result = fromBinary(event.data.data.value.toArray, clazz)
    if (manifest.isInstance(result)) result
    else sys.error(s"Cannot deserialize event as $manifest, event: $event")
  }

  def classFor(x: AnyRef) = x match {
    case x: PersistentRepr => classOf[PersistentRepr]
    case _                 => x.getClass
  }

  object ActorRefSerializer extends Serializer[ActorRef] {
    val Clazz = classOf[ActorRef]

    def deserialize(implicit format: Formats) = {
      case (TypeInfo(Clazz, _), JString(x)) => system.provider.resolveActorRef(x)
    }

    def serialize(implicit format: Formats) = {
      case x: ActorRef => JString(x.path.toSerializationFormat)
    }
  }
}

object Json4sSerializer {
  val UTF8: Charset = Charset.forName("UTF-8")
  val Identifier: Int = ByteBuffer.wrap("json4s".getBytes(UTF8)).getInt

  object SnapshotSerializer extends Serializer[Snapshot] {
    val Clazz = classOf[Snapshot]

    def deserialize(implicit format: Formats) = {
      case (TypeInfo(Clazz, _), JObject(List(
        JField("data", JString(x)),
        JField("metadata", metadata)))) => Snapshot(x, metadata.extract[SnapshotMetadata])
    }

    def serialize(implicit format: Formats) = {
      case Snapshot(data, metadata) => JObject("data" -> JString(data.toString), "metadata" -> decompose(metadata))
    }
  }

  object PersistentReprSerializer extends Serializer[PersistentRepr] {
    val Clazz = classOf[PersistentRepr]

    def deserialize(implicit format: Formats) = {
      case (TypeInfo(Clazz, _), json) =>
        val x = json.extract[Mapping]
        PersistentRepr(x.payload, x.sequenceNr, x.persistenceId, deleted = x.deleted, sender = x.sender)
    }
    def serialize(implicit format: Formats) = {
      case x: PersistentRepr =>
        val mapping = Mapping(x.payload.asInstanceOf[String], x.sequenceNr, x.persistenceId, x.deleted, x.sender)
        decompose(mapping)
    }

    case class Mapping(payload: String, sequenceNr: Long, persistenceId: String, deleted: Boolean, sender: ActorRef)
  }
}