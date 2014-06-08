package akka.persistence.eventstore

import akka.actor.{ ActorLogging, Actor }
import akka.serialization.{ SerializationExtension, Serialization }
import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }
import eventstore.{ ContentType, Content, EsConnection, EventStoreExtension }
import akka.util.ByteString

trait EventStorePlugin extends ActorLogging { self: Actor =>
  val connection: EsConnection = EventStoreExtension(context.system).connection
  private val serialization: Serialization = SerializationExtension(context.system)
  import context.dispatcher

  def deserialize[T](content: Content, clazz: Class[T]): T = {
    serialization.deserialize(content.value.toArray, clazz).get
  }

  def serialize(x: AnyRef): Content = {
    val serializer = serialization.findSerializerFor(x)
    val contentType = serializer match {
      case x: EventStoreSerializer => x.contentType
      case _                       => ContentType.Binary
    }
    Content(ByteString(serializer.toBinary(x)), contentType)
  }

  def asyncUnit(x: => Future[_]): Future[Unit] = async(x).map(_ => Unit)

  def async[T](x: => Future[T]): Future[T] = Try(x) match {
    case Success(s) => s
    case Failure(f) => Future.failed(f)
  }

  def asyncSeq[A](x: => Iterable[Future[A]]): Future[Unit] = asyncUnit(Future.sequence(x))

  def logNoClassFoundFor(eventType: String): Unit = {
    log.warning("Can't find class to deserialize eventType {}", eventType)
  }
}