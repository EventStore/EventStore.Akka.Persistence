package akka.persistence.eventstore.journal

import akka.actor._
import akka.persistence.PersistentActor
import akka.testkit._
import org.scalatest._

import scala.concurrent.duration._

object JournalLoadIntegrationSpec {
  trait Measure {
    this: Actor ⇒
    val NanoToSecond = 1000.0 * 1000 * 1000

    var startTime: Long = 0L
    var stopTime: Long = 0L

    var startSequenceNr = 0L
    var stopSequenceNr = 0L

    def startMeasure(): Unit = {
      startSequenceNr = lastSequenceNr
      startTime = System.nanoTime
    }

    def stopMeasure(): Unit = {
      stopSequenceNr = lastSequenceNr
      stopTime = System.nanoTime
      sender ! (NanoToSecond * (stopSequenceNr - startSequenceNr) / (stopTime - startTime))
    }

    def lastSequenceNr: Long
  }

  class TestPersistentActor(val persistenceId: String) extends PersistentActor with Measure {
    def receiveRecover: Receive = handle

    def receiveCommand: Receive = {
      case c @ "start"     => defer(c) { _ => startMeasure(); sender ! "started" }
      case c @ "stop"      => defer(c)(_ => stopMeasure())
      case payload: String => persistAsync(payload)(handle)
    }

    def handle: Receive = {
      case payload: String =>
    }
  }
}

class JournalLoadIntegrationSpec extends TestKit(ActorSystem("test")) with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll {
  import akka.persistence.eventstore.journal.JournalLoadIntegrationSpec._

  override def afterAll() = system.shutdown()

  "An Event Store Journal" must {
    "have some reasonable throughput" in {
      val warmCycles = 1000L
      val loadCycles = 10000L // set to 300000L to get reasonable results
      val processor1 = system.actorOf(Props(classOf[TestPersistentActor], "test"))
      1L to warmCycles foreach { i => processor1 ! "a" }
      processor1 ! "start"
      expectMsg("started")
      1L to loadCycles foreach { i => processor1 ! "a" }
      processor1 ! "stop"
      val throughput = expectMsgType[Double](20.seconds)
      println(f"\nthroughput = $throughput%.2f persistent commands per second")
    }
  }
}
