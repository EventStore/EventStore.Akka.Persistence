package akka.persistence.eventstore.journal

import com.typesafe.config.ConfigFactory

class Json4sJournalIntegrationSpec
  extends JournalIntegrationSpec(ConfigFactory.load("json4s.conf"))
