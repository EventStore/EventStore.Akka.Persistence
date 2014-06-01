package akka.persistence.eventstore

import akka.actor.{ ActorLogging, Actor }
import akka.serialization.{ SerializationExtension, Serialization }
import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }
import eventstore.{ Content, EsConnection }

trait EventStorePlugin extends ActorLogging { self: Actor =>
  val connection: EsConnection = EsConnection(context.system)
  private val serialization: Serialization = SerializationExtension(context.system)
  import context.dispatcher

  def deserialize[T](content: Content, clazz: Class[T]): T = {
    serialization.deserialize(content.value.toArray, clazz).get
  }

  def serialize(x: AnyRef): Content = {
    Content(serialization.serialize(x).get)
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