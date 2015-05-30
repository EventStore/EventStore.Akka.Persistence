package akka.persistence.eventstore

import akka.actor.{ Actor, ActorLogging }
import com.typesafe.config.Config
import eventstore._
import scala.concurrent.Future
import scala.util.control.NonFatal

trait EventStorePlugin extends ActorLogging { self: Actor =>
  val connection: EsConnection = EventStoreExtension(context.system).connection
  val serialization = EventStoreSerialization(context.system)
  val prefix: String = config.getString("stream-prefix")
  import context.dispatcher

  def config: Config

  def asyncUnit(x: => Future[_]): Future[Unit] = async(x).map[Unit](_ => ())

  def async[T](x: => Future[T]): Future[T] = try x catch {
    case NonFatal(f) => Future.failed(f)
  }
}