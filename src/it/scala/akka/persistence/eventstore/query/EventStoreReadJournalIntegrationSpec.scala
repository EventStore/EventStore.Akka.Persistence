package akka.persistence.eventstore.query

import java.util.UUID
import scala.concurrent.duration._
import akka.actor.Props
import akka.persistence.PersistentActor
import akka.persistence.eventstore.ActorSpec
import akka.persistence.eventstore.query.scaladsl.EventStoreReadJournal
import akka.persistence.query.{EventEnvelope, PersistenceQuery, Sequence}
import akka.stream.ActorMaterializer
import akka.stream.testkit.scaladsl.TestSink
import org.scalatest.Matchers

class EventStoreReadJournalIntegrationSpec extends ActorSpec with Matchers {
  implicit val materializer = ActorMaterializer()

  def queries = PersistenceQuery(system).
    readJournalFor[EventStoreReadJournal](EventStoreReadJournal.Identifier)

  "EventStore query persistence ids" should {

    "find persistence ids" in new Scope {
      val persistenceIds = List.fill(5)(randomId("find_all_"))
      for { persistenceId <- persistenceIds } {
        system.actorOf(TestActor.props(persistenceId)) ! persistenceId
        expectMsg(s"$persistenceId-done")
      }

      expectNoMessage(100.millis) // Give ES some time to write to $streams

      val src = queries.persistenceIds().filter { x => persistenceIds contains x }
      src.runWith(TestSink.probe[String])
        .request(persistenceIds.size.toLong)
        .expectNextUnorderedN(persistenceIds)
    }
  }

  "EventStore query current persistence ids" should {

    "find persistence ids" in new Scope {
      val persistenceIds = List.fill(5)(randomId("find_current_"))
      for { persistenceId <- persistenceIds } {
        system.actorOf(TestActor.props(persistenceId)) ! persistenceId
        expectMsg(s"$persistenceId-done")
      }

      expectNoMessage(100.millis) // Give ES some time to write to $streams

      val src = queries.currentPersistenceIds().filter { x => persistenceIds contains x }
      src.runWith(TestSink.probe[String])
        .request(persistenceIds.size.toLong)
        .expectNextUnorderedN(persistenceIds)
        .expectComplete()
    }
  }

  "EventStore query events by persistenceId" should {

    "find existing events" in new Scope {
      val envelopes = write(10) take 5
      val src = queries.eventsByPersistenceId(persistenceId, 0, Long.MaxValue)
      src.runWith(TestSink.probe[EventEnvelope])
        .request(5)
        .expectNextN(envelopes)
    }

    "find new events" in new Scope {
      val src = queries.eventsByPersistenceId(persistenceId, 0, Long.MaxValue)
      val envelopes = write(10) take 5
      src.runWith(TestSink.probe[EventEnvelope])
        .request(5)
        .expectNextN(envelopes)
    }

    "find existing events in defined range" in new Scope {
      val envelopes = write(10).slice(1, 3)
      val src = queries.eventsByPersistenceId(persistenceId, 1, 3)
      src.runWith(TestSink.probe[EventEnvelope])
        .request(3)
        .expectNextN(envelopes)
    }

    "find new events in defined range" in new Scope {
      val src = queries.eventsByPersistenceId(persistenceId, 1, 3)
      val envelopes = write(10).slice(1, 3)
      src.runWith(TestSink.probe[EventEnvelope])
        .request(3)
        .expectNextN(envelopes)
    }
  }

  "EventStore query current events by persistenceId" should {

    "find events" in new Scope {
      val envelopes = write(5)
      val src = queries.currentEventsByPersistenceId(persistenceId, 0, Long.MaxValue)
      src.runWith(TestSink.probe[EventEnvelope])
        .request(5)
        .expectNextN(envelopes)
        .expectComplete()
    }

    "find no events" in new Scope {
      val src = queries.currentEventsByPersistenceId(persistenceId, 0, Long.MaxValue)
      src.runWith(TestSink.probe[EventEnvelope])
        .request(1)
        .expectComplete()
    }

    "find events in defined range" in new Scope {
      val envelopes = write(5).slice(1, 3)
      val src = queries.currentEventsByPersistenceId(persistenceId, 1, 3)
      src.runWith(TestSink.probe[EventEnvelope])
        .request(3)
        .expectNextN(envelopes)
        .expectComplete()
    }
  }

  private trait Scope extends ActorScope {

    val persistenceId = randomId()

    lazy val ref = system.actorOf(TestActor.props(persistenceId))

    def randomId(prefix: String = ""): String = s"$prefix${UUID.randomUUID()}"

    def write(n: Int): List[EventEnvelope] = {
      val events = List.fill(n)(randomId())
      for { event <- events } {
        ref ! event
        expectMsg(s"$event-done")
      }

      for { (event, idx) <- events.zipWithIndex } yield {
        val seqNr = idx + 1
        EventEnvelope(
          offset = Sequence(seqNr.toLong),
          persistenceId = persistenceId,
          sequenceNr = seqNr.toLong,
          event = event
        )
      }
    }

    object TestActor {
      def props(persistenceId: String): Props = {
        Props(new TestActor(persistenceId))
      }
    }

    class TestActor(val persistenceId: String) extends PersistentActor {

      val receiveRecover: Receive = { case _: String => }

      val receiveCommand: Receive = {
        case cmd: String =>
          persist(cmd) { evt =>
            sender() ! evt + "-done"
          }
      }
    }
  }
}
