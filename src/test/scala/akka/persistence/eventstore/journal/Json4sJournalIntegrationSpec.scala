package akka.persistence.eventstore.journal

import com.typesafe.config.{ ConfigFactory, Config }

class Json4sJournalIntegrationSpec extends JournalIntegrationSpec {
  override lazy val config: Config = ConfigFactory.load("json4s.conf")
}
