package akka.persistence.eventstore.journal

import akka.persistence.CapabilityFlag
import akka.persistence.eventstore.EventStorePluginSpec
import akka.persistence.journal.JournalPerfSpec
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration._

class JournalPerfIntegrationSpec
    extends JournalPerfSpec(ConfigFactory.load()) with EventStorePluginSpec {

  protected def supportsRejectingNonSerializableObjects = CapabilityFlag.off()

  override def awaitDurationMillis = 30.seconds.toMillis
}