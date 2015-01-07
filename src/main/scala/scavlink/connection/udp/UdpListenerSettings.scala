package scavlink.connection.udp

import com.typesafe.config.Config
import scavlink.connection.ConnectionSettings
import scavlink.settings.SettingsCompanion

case class UdpListenerSettings(interface: String, port: Int) extends ConnectionSettings {
  val actorName = "udp-listener:" + port
}

object UdpListenerSettings extends SettingsCompanion[UdpListenerSettings]("udp-listener") {
  override def apply(config: Config): UdpListenerSettings = UdpListenerSettings(
    config.getString("interface"),
    config.getInt("port")
  )

  def fromSubConfig(config: Config): UdpListenerSettings = apply(config)
}