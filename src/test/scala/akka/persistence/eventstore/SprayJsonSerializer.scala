package akka.persistence.eventstore

import java.nio.charset.Charset

import java.nio.ByteBuffer
import akka.persistence.SnapshotMetadata
import akka.persistence.eventstore.snapshot.EventStoreSnapshotStore.SnapshotEvent
import akka.persistence.eventstore.snapshot.EventStoreSnapshotStore.SnapshotEvent.Snapshot
import eventstore.ContentType
import spray.json._

import scala.reflect.ClassTag

class SprayJsonSerializer extends EventStoreSerializer {
  import SprayJsonSerializer._
  import SprayJsonSerializer.JsonProtocol._

  def identifier = Identifier

  def includeManifest = true

  def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]) = {
    def fromBinary(manifest: Class[_]) = {
      val json = new String(bytes, UTF8).parseJson
      val format = classFormatMap(manifest)
      format.read(json)
    }
    fromBinary(manifest getOrElse sys.error("manifest is missing"))
  }

  def toBinary(x: AnyRef) = {
    val json = classFormatMap(x.getClass).write(x)
    val str = json.compactPrint
    str.getBytes(UTF8)
  }

  def contentType = ContentType.Json
}

object SprayJsonSerializer {
  val UTF8 = Charset.forName("UTF-8")
  val Identifier: Int = ByteBuffer.wrap("spray-json".getBytes(UTF8)).getInt

  object JsonProtocol extends DefaultJsonProtocol {
    val ClassFormatMap = Map(
      entry(jsonFormat3(SnapshotMetadata.apply)),
      entry(jsonFormat2(SnapshotEvent.DeleteCriteria.apply)),
      entry(jsonFormat2(SnapshotEvent.Delete.apply)),
      entry(SnapshotFormat))

    def classFormatMap(x: Class[_]) = ClassFormatMap.getOrElse(x, sys.error(s"JsonFormat not found for $x"))

    def entry[T](format: JsonFormat[T])(implicit tag: ClassTag[T]): (Class[_], JsonFormat[AnyRef]) = {
      tag.runtimeClass -> format.asInstanceOf[JsonFormat[AnyRef]]
    }

    object SnapshotFormat extends JsonFormat[Snapshot] {
      def read(json: JsValue) = json.asJsObject.getFields("data") match {
        case Seq(JsString(x)) => Snapshot(x)
        case _                => deserializationError("string expected")
      }

      def write(obj: Snapshot) = obj.data match {
        case x: String => JsObject("data" -> JsString(x))
        case _         => serializationError("string expected")
      }
    }
  }
}