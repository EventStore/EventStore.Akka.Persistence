package akka.persistence.eventstore

import akka.persistence.eventstore.snapshot.EventStoreSnapshotStore.SnapshotEvent.Snapshot
import org.json4s._
import org.json4s.native.Serialization.{ read, write }
import java.nio.charset.Charset
import java.nio.ByteBuffer
import snapshot.EventStoreSnapshotStore.SnapshotEvent.SnapshotClass
import eventstore.ContentType

class Json4sSerializer extends EventStoreSerializer {
  import Json4sSerializer._

  def identifier = Identifier

  def includeManifest = true

  def fromBinary(bytes: Array[Byte], manifestOpt: Option[Class[_]]) = {
    implicit val manifest = manifestOpt match {
      case Some(x) => Manifest.classType[AnyRef](x)
      case None    => Manifest.AnyRef
    }
    read(new String(bytes, UTF8))
  }

  def toBinary(o: AnyRef) = write(o).getBytes(UTF8)

  def contentType = ContentType.Json
}

object Json4sSerializer {

  val UTF8 = Charset.forName("UTF-8")
  val Identifier: Int = ByteBuffer.wrap("json4s".getBytes(UTF8)).getInt
  implicit val formats: Formats = DefaultFormats + SnapshotSerializer

  object SnapshotSerializer extends Serializer[Snapshot] {

    def deserialize(implicit format: Formats) = {
      case (TypeInfo(SnapshotClass, _), JObject(List(JField("data", JString(x))))) => Snapshot(x)
    }

    def serialize(implicit format: Formats) = {
      case Snapshot(x: String) => JObject("data" -> JString(x))
    }
  }
}