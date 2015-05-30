package akka.persistence.eventstore.journal

import com.typesafe.config.ConfigFactory

class SprayJsonJournalIntegrationSpec
  extends JournalIntegrationSpec(ConfigFactory.load("spray-json.conf"))