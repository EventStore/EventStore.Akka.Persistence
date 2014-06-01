package akka.persistence.eventstore

import akka.persistence.PluginSpec
import com.typesafe.config.ConfigFactory
import java.util.UUID

trait EventStorePluginSpec extends PluginSpec {
  lazy val config = ConfigFactory.load()

  private var _pid: String = _

  protected override def beforeEach() = {
    val uuid = UUID.randomUUID().toString
    _pid = s"processor-$uuid"
    super.beforeEach()
  }

  override def pid = _pid
}
