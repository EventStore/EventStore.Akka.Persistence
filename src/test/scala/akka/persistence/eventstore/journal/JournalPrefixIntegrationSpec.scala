package akka.persistence.eventstore.journal

import com.typesafe.config.ConfigFactory

class JournalPrefixIntegrationSpec
  extends JournalIntegrationSpec(ConfigFactory.load("stream-prefix.conf"))
