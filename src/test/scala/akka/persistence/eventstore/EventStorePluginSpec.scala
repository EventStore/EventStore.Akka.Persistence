package akka.persistence.eventstore

import akka.persistence.PluginSpec
import java.util.UUID

trait EventStorePluginSpec extends PluginSpec {
  private var _pid: String = _

  protected override def beforeEach() = {
    val uuid = UUID.randomUUID().toString
    _pid = s"processor-$uuid"
    super.beforeEach()
  }

  override def pid = _pid
}
