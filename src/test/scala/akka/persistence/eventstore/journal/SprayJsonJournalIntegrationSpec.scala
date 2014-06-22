package akka.persistence.eventstore.journal

import com.typesafe.config.{ ConfigFactory, Config }

class SprayJsonJournalIntegrationSpec extends JournalIntegrationSpec {
  override lazy val config: Config = ConfigFactory.load("spray-json.conf")
}