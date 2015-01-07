package scavlink.connection

import java.net.InetSocketAddress

import scavlink.settings.SettingsCompanion
import com.typesafe.config.Config

case class BridgeSettings(isEnabled: Boolean, address: InetSocketAddress, allowReceive: Boolean = false)

object BridgeSettings extends SettingsCompanion[BridgeSettings]("bridge") {
  def fromSubConfig(config: Config) = BridgeSettings(
    config.getBoolean("enabled"),
    new InetSocketAddress(config.getString("address"), config.getInt("port")),
    config.getBoolean("allow-receive")
  )
}
