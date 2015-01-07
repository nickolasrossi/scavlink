package scavlink.connection.tcp

import java.net.InetSocketAddress

import com.typesafe.config.Config
import scavlink.connection.ConnectionSettings
import scavlink.settings.SettingsCompanion

import scala.concurrent.duration.FiniteDuration

case class TcpClientSettings(server: InetSocketAddress, timeout: FiniteDuration, reconnectInterval: FiniteDuration)
  extends ConnectionSettings {
  val actorName = ("tcp-client:" + server).replace("/", "")
}

object TcpClientSettings extends SettingsCompanion[TcpClientSettings]("tcp-client") {
  override def apply(config: Config): TcpClientSettings = {
    TcpClientSettings(
      new InetSocketAddress(config.getString("host"), config.getInt("port")),
      getDuration(config, "connect-timeout"),
      getDuration(config, "reconnect-interval")
    )
  }

  def fromSubConfig(config: Config) = apply(config)
}