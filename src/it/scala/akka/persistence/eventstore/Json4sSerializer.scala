package akka.persistence.eventstore

import java.nio.ByteBuffer
import java.nio.charset.Charset

import scala.annotation.nowarn
import akka.actor.{ ActorRef, ExtendedActorSystem }
import akka.persistence.eventstore.snapshot.EventStoreSnapshotStore.SnapshotEvent
import akka.persistence.eventstore.snapshot.EventStoreSnapshotStore.SnapshotEvent.Snapshot
import akka.persistence.{ PersistentRepr, SnapshotMetadata }
import eventstore.core.util.uuid.randomUuid
import eventstore.{Content, ByteString, ContentType, Event, EventData}
import org.json4s.Extraction.decompose
import org.json4s._
import org.json4s.native.Serialization.{ read, write }

class Json4sSerializer(val system: ExtendedActorSystem) extends EventStoreSerializer {
  import Json4sSerializer._

  implicit val formats: Formats = 
    DefaultFormats + SnapshotSerializer + new PersistentReprSerializer(system) + ActorRefSerializer

  def identifier: Int = Identifier

  def includeManifest: Boolean = true

  def fromBinary(bytes: Array[Byte], manifestOpt: Option[Class[_]]): AnyRef = {
    implicit val manifest = manifestOpt match {
      case Some(x) => Manifest.classType(x)
      case None    => Manifest.AnyRef
    }
    read(new String(bytes, UTF8))
  }

  def toBinary(o: AnyRef): Array[Byte] = write(o).getBytes(UTF8)

  def toEvent(x: AnyRef): EventData = x match {
    case x: PersistentRepr => EventData(
      eventType = classFor(x).getName,
      eventId = randomUuid,
      data = Content(ByteString(toBinary(x)), ContentType.Json)
    )

    case x: SnapshotEvent => EventData(
      eventType = classFor(x).getName,
      eventId = randomUuid,
      data = Content(ByteString(toBinary(x)), ContentType.Json)
    )

    case _ => sys.error(s"Cannot serialize $x, SnapshotEvent expected")
  }

  def fromEvent(event: Event, manifest: Class[_]): AnyRef = {
    val clazz = Class.forName(event.data.eventType)
    val result = fromBinary(event.data.data.value.toArray, clazz)
    if (manifest.isInstance(result)) result
    else sys.error(s"Cannot deserialize event as $manifest, event: $event")
  }

  private def classFor(x: AnyRef) = x match {
    case _: PersistentRepr => classOf[PersistentRepr]
    case _                 => x.getClass
  }

  object ActorRefSerializer extends Serializer[ActorRef] {
    private val Clazz = classOf[ActorRef]

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


  object SnapshotMeatadataFormat extends JsonFormat[SnapshotMetadata] {

    def readUnsafe(value: JValue): SnapshotMetadata =
      readEither(value).fold(throw _, identity)

    def readEither(value: JValue): Either[MappingException, SnapshotMetadata] = value match {
      case JObject(JField("persistenceId", JString(pid)) :: JField("sequenceNr", JInt(sn)) :: JField("timestamp", JInt(ts)) :: Nil) =>
        Right(SnapshotMetadata(pid, sn.longValue, ts.longValue))
      case m =>
        Left(new MappingException(s"Unknown json: $m"))
    }

    def write(sm: SnapshotMetadata): JValue = JObject(
      JField("persistenceId", JString(sm.persistenceId)),
      JField("sequenceNr", JLong(sm.sequenceNr)),
      JField("timestamp", JLong(sm.timestamp))
    )
  }

  object SnapshotSerializer extends Serializer[Snapshot] {
    val Clazz: Class[Snapshot] = classOf[Snapshot]

    def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), Snapshot] = {
      case (TypeInfo(Clazz, _), JObject(List(
        JField("data", JString(x)),
        JField("metadata", metadata)))) => Snapshot(x, SnapshotMeatadataFormat.readUnsafe(metadata))
    }

    def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
      case Snapshot(data, metadata) => JObject("data" -> JString(data.toString), "metadata" -> SnapshotMeatadataFormat.write(metadata))
    }
  }

  class PersistentReprSerializer(system: ExtendedActorSystem) extends Serializer[PersistentRepr] {
    val Clazz: Class[PersistentRepr] = classOf[PersistentRepr]

    @nowarn
    def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), PersistentRepr] = {
      case (TypeInfo(Clazz, _), json) =>
        val x = json.extract[Mapping]
        PersistentRepr(
          payload = x.payload,
          sequenceNr = x.sequenceNr,
          persistenceId = x.persistenceId,
          manifest = x.manifest,
          writerUuid = x.writerUuid
        )
    }
    def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
      case x: PersistentRepr =>
        val mapping = Mapping(
          payload = x.payload.asInstanceOf[String],
          sequenceNr = x.sequenceNr,
          persistenceId = x.persistenceId,
          manifest = x.manifest,
          writerUuid = x.writerUuid
        )
        decompose(mapping)
    }
  }

  case class Mapping(
    payload:       String,
    sequenceNr:    Long,
    persistenceId: String,
    manifest:      String,
    writerUuid:    String
  )
}