package akka.persistence.eventstore.journal

import akka.persistence.CapabilityFlag
import akka.persistence.eventstore.EventStorePluginSpec
import akka.persistence.journal.JournalSpec
import com.typesafe.config.{ Config, ConfigFactory }

class JournalIntegrationSpec(config: Config)
    extends JournalSpec(config) with EventStorePluginSpec {

  override protected def supportsRejectingNonSerializableObjects: CapabilityFlag = CapabilityFlag.off()

  def this() = this(ConfigFactory.load())
}