package akka.persistence.eventstore.journal

import akka.persistence.eventstore.EventStorePluginSpec
import akka.persistence.journal.JournalSpec
import com.typesafe.config.{ Config, ConfigFactory }

class JournalIntegrationSpec(config: Config)
    extends JournalSpec(config) with EventStorePluginSpec {

  def supportsRejectingNonSerializableObjects = false

  def this() = this(ConfigFactory.load())
}