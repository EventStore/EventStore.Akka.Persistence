package akka.persistence.eventstore

import java.util.UUID

import akka.persistence.PluginSpec

trait EventStorePluginSpec extends PluginSpec {
  private var _pid: String = _

  protected override def beforeEach() = {
    _pid = UUID.randomUUID().toString
    super.beforeEach()
  }

  override def pid = _pid
}
