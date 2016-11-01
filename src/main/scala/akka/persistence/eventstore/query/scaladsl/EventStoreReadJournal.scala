package akka.persistence.eventstore.query.scaladsl

import akka.actor.ExtendedActorSystem
import akka.persistence.PersistentRepr
import akka.persistence.eventstore.EventStoreSerialization
import akka.persistence.eventstore.Helpers._
import akka.persistence.query.EventEnvelope
import akka.persistence.query.scaladsl._
import akka.stream.scaladsl.Source
import com.typesafe.config.Config
import eventstore.{ EventNumber, EventStoreExtension, EventStream }

class EventStoreReadJournal(system: ExtendedActorSystem, config: Config)
    extends ReadJournal
    with AllPersistenceIdsQuery
    with CurrentPersistenceIdsQuery
    with EventsByPersistenceIdQuery
    with CurrentEventsByPersistenceIdQuery {

  private val serialization = EventStoreSerialization(system)

  def currentPersistenceIds() = {
    persistenceIds(infinite = false) named "currentPersistenceIds"
  }

  def allPersistenceIds() = {
    persistenceIds(infinite = true) named "allPersistenceIds"
  }

  def eventsByPersistenceId(persistenceId: String, from: Long, to: Long) = {
    eventsByPersistenceId(persistenceId, from, to, infinite = true)
      .named(s"eventsByPersistenceId-$persistenceId-$from-$to")
  }

  def currentEventsByPersistenceId(persistenceId: String, from: Long, to: Long) = {
    eventsByPersistenceId(persistenceId, from, to, infinite = false)
      .named(s"currentEventsByPersistenceId-$persistenceId-$from-$to")
  }

  private def eventsByPersistenceId(persistenceId: String, from: Long, to: Long, infinite: Boolean): Source[EventEnvelope, akka.NotUsed] = {

    def eventsByPersistenceId(from: Option[EventNumber], to: EventNumber) = {
      val streamId = EventStream.Id(persistenceId)
      val publisher = connection.streamPublisher(
        streamId,
        fromNumberExclusive = from,
        infinite = infinite,
        resolveLinkTos = true
      )
      Source.fromPublisher(publisher)
        .takeWhile { _.record.number <= to }
        .map { x =>
          val sequenceNr = sequenceNumber(x.record.number)
          EventEnvelope(
            offset = sequenceNr,
            persistenceId = persistenceId,
            sequenceNr = sequenceNr,
            event = serialization.deserialize[PersistentRepr](x).payload
          )
        }
    }

    eventsByPersistenceId(
      if (from == 0) None else Some(eventNumber(from)),
      if (to > Int.MaxValue) EventNumber.Last else eventNumber(to)
    )
  }

  private def persistenceIds(infinite: Boolean): Source[String, akka.NotUsed] = {
    val streamId = EventStream.System.`$streams`
    val publisher = connection.streamPublisher(streamId, infinite = infinite, resolveLinkTos = true)
    Source.fromPublisher(publisher) map { x => x.streamId.streamId }
  }

  private def connection = EventStoreExtension(system).connection
}

object EventStoreReadJournal {
  final val Identifier: String = "eventstore.persistence.query"
}