package scavlink.connection

import com.typesafe.config.Config
import scavlink.message.{ComponentId, SystemId}
import scavlink.settings.SettingsCompanion

import scala.concurrent.duration.FiniteDuration

case class HeartbeatSettings(thisSystemId: SystemId,
                             thisComponentId: ComponentId,
                             interval: FiniteDuration,
                             timeout: FiniteDuration)

object HeartbeatSettings extends SettingsCompanion[HeartbeatSettings]("heartbeat") {
  def fromSubConfig(config: Config): HeartbeatSettings = {
    HeartbeatSettings(
      SystemId(config.getInt("this-system-id")),
      ComponentId(config.getInt("this-component-id")),
      getDuration(config, "heartbeat-interval"),
      getDuration(config, "heartbeat-timeout")
    )
  }
}
