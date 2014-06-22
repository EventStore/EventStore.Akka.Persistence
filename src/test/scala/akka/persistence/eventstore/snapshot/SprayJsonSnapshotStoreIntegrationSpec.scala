package akka.persistence.eventstore.snapshot

import com.typesafe.config.{ ConfigFactory, Config }

class SprayJsonSnapshotStoreIntegrationSpec extends SnapshotStoreIntegrationSpec {
  override lazy val config: Config = ConfigFactory.load("spray-json.conf")
}