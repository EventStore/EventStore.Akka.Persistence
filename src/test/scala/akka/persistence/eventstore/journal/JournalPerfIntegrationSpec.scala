package akka.persistence.eventstore.journal

import akka.persistence.journal.JournalPerfSpec
import scala.concurrent.duration._

class JournalPerfIntegrationSpec extends JournalIntegrationSpec with JournalPerfSpec {
  override def awaitDurationMillis = 30.seconds.toMillis
}