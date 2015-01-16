package scavlink.link

import akka.util.Timeout
import com.typesafe.config.Config
import scavlink.settings.SettingsCompanion

import scala.concurrent.duration.FiniteDuration

case class VehicleSettings(apiTimeout: Timeout,
                           channelOverrideInterval: FiniteDuration,
                           autoloadParameters: Boolean,
                           autoloadMission: Boolean,
                           autostartTelemetry: Boolean)

object VehicleSettings extends SettingsCompanion[VehicleSettings]("vehicle") {
  def fromSubConfig(config: Config): VehicleSettings =
    VehicleSettings(
      getDuration(config, "api-timeout"),
      getDuration(config, "channel-override-interval"),
      config.getBoolean("autoload-parameters"),
      config.getBoolean("autoload-mission"),
      config.getBoolean("autostart-telemetry")
    )
}
