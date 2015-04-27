package akka.persistence.eventstore.snapshot

import com.typesafe.config.{ ConfigFactory, Config }

class SnapshotPrefixSpaceIntegrationSpec extends SnapshotStoreIntegrationSpec {
  override lazy val config: Config = ConfigFactory.load("stream-prefix.conf")
}
