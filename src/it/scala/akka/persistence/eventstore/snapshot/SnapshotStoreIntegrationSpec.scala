package akka.persistence.eventstore.snapshot

import akka.persistence.eventstore.EventStorePluginSpec
import akka.persistence.snapshot.SnapshotStoreSpec
import com.typesafe.config.{ Config, ConfigFactory }

class SnapshotStoreIntegrationSpec(config: Config)
    extends SnapshotStoreSpec(config) with EventStorePluginSpec {

  def this() = this(ConfigFactory.load())
}

