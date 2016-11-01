package akka.persistence.eventstore

import akka.actor.ActorSystem
import akka.testkit.{ ImplicitSender, TestKit }
import com.typesafe.config.ConfigFactory
import org.scalatest.{ WordSpecLike, BeforeAndAfterAll, FlatSpec }

class ActorSpec extends WordSpecLike with BeforeAndAfterAll {
  implicit lazy val system: ActorSystem = ActorSystem(getClass.getSimpleName, config)

  def config = ConfigFactory.load()

  override protected def afterAll() = TestKit.shutdownActorSystem(system)

  abstract class ActorScope extends TestKit(system) with ImplicitSender
}