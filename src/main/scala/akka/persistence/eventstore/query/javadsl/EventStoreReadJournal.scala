package akka.persistence.eventstore.query.javadsl

import akka.persistence.eventstore.query.scaladsl
import akka.persistence.query.javadsl._

class EventStoreReadJournal(readJournal: scaladsl.EventStoreReadJournal)
    extends ReadJournal
    with AllPersistenceIdsQuery
    with CurrentPersistenceIdsQuery
    with EventsByPersistenceIdQuery
    with CurrentEventsByPersistenceIdQuery
    with EventsByTagQuery
    with CurrentEventsByTagQuery {

  def allPersistenceIds() = {
    readJournal.allPersistenceIds().asJava
  }

  def currentPersistenceIds() = {
    readJournal.currentPersistenceIds().asJava
  }

  def eventsByPersistenceId(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long) = {
    readJournal.eventsByPersistenceId(persistenceId, fromSequenceNr, toSequenceNr).asJava
  }

  def currentEventsByPersistenceId(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long) = {
    readJournal.currentEventsByPersistenceId(persistenceId, fromSequenceNr, toSequenceNr).asJava
  }

  def eventsByTag(tag: String, offset: Long) = {
    readJournal.eventsByTag(tag, offset).asJava
  }

  override def currentEventsByTag(tag: String, offset: Long) = {
    readJournal.currentEventsByTag(tag, offset).asJava
  }
}
