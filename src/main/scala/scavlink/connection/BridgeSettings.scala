package scavlink.connection

import java.net.InetSocketAddress

import scavlink.settings.SettingsCompanion
import com.typesafe.config.Config

case class BridgeSettings(isEnabled: Boolean, address: InetSocketAddress, isTwoWay: Boolean)

object BridgeSettings extends SettingsCompanion[BridgeSettings]("bridge") {
  def fromSubConfig(config: Config) = BridgeSettings(
    config.getBoolean("enabled"),
    new InetSocketAddress(config.getString("host"), config.getInt("port")),
    config.getBoolean("two-way")
  )
}
