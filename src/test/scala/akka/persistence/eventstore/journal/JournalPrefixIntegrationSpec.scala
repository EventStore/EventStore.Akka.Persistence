package akka.persistence.eventstore.journal

import com.typesafe.config.{ ConfigFactory, Config }

class JournalPrefixIntegrationSpec extends JournalIntegrationSpec {
  override lazy val config: Config = ConfigFactory.load("stream-prefix.conf")
}
