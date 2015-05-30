package akka.persistence.eventstore.snapshot

import com.typesafe.config.ConfigFactory

class SnapshotPrefixSpaceIntegrationSpec
  extends SnapshotStoreIntegrationSpec(ConfigFactory.load("stream-prefix.conf"))
