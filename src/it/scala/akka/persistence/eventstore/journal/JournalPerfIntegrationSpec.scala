package akka.persistence.eventstore.journal

import akka.persistence.eventstore.EventStorePluginSpec
import akka.persistence.journal.JournalPerfSpec
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration._

class JournalPerfIntegrationSpec extends JournalPerfSpec(ConfigFactory.load()) with EventStorePluginSpec {

  def supportsRejectingNonSerializableObjects = false
}