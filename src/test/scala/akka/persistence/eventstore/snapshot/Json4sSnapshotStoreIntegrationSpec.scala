package akka.persistence.eventstore.snapshot

import com.typesafe.config.{ ConfigFactory, Config }

class Json4sSnapshotStoreIntegrationSpec extends SnapshotStoreIntegrationSpec {
  override lazy val config: Config = ConfigFactory.load("json4s.conf")
}