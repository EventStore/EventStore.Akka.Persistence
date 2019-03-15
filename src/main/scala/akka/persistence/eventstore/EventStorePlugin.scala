package akka.persistence.eventstore

import akka.actor.{ Actor, ActorLogging }
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import eventstore.akka.Settings
import eventstore.akka._
import eventstore.akka.tcp.ConnectionActor
import scala.concurrent.Future
import scala.util.control.NonFatal

trait EventStorePlugin extends ActorLogging { self: Actor =>
  val settings: Settings = Settings(context.system.settings.config)

  val connection: EsConnection = {
    val dispatcher = config.getString("plugin-dispatcher")
    val props = ConnectionActor.props(settings).withDispatcher(dispatcher)
    val ref = context.actorOf(props, "eventstore")
    new EsConnection(ref, context, settings)
  }

  val serialization = EventStoreSerialization(context.system)
  implicit val materializer = ActorMaterializer()(context)
  val prefix: String = config.getString("stream-prefix")
  import context.dispatcher

  def config: Config

  def asyncUnit(x: => Future[_]): Future[Unit] = async(x).map(_ => ())

  def async[T](x: => Future[T]): Future[T] = try x catch {
    case NonFatal(f) => Future.failed(f)
  }
}