package akka.persistence.eventstore.snapshot

import com.typesafe.config.ConfigFactory

class SprayJsonSnapshotStoreIntegrationSpec
  extends SnapshotStoreIntegrationSpec(ConfigFactory.load("spray-json.conf")) {

  override def supportsSerialization = false
}