package akka.persistence.eventstore.query

import akka.actor.ExtendedActorSystem
import akka.persistence.query.ReadJournalProvider
import com.typesafe.config.Config

class EventStoreReadJournalProvider(system: ExtendedActorSystem, config: Config) extends ReadJournalProvider {

  def scaladslReadJournal(): scaladsl.EventStoreReadJournal = {
    new scaladsl.EventStoreReadJournal(system, config)
  }

  def javadslReadJournal(): javadsl.EventStoreReadJournal = {
    new javadsl.EventStoreReadJournal(scaladslReadJournal())
  }
}
