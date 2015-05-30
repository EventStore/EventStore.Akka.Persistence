package akka.persistence.eventstore.snapshot

import com.typesafe.config.ConfigFactory

class Json4sSnapshotStoreIntegrationSpec
  extends SnapshotStoreIntegrationSpec(ConfigFactory.load("json4s.conf"))