package scavlink.connection

import scavlink.connection.serial.{SerialClient, SerialClientSettings}
import scavlink.connection.tcp.{TcpClient, TcpClientSettings}
import scavlink.connection.udp.{UdpListener, UdpListenerSettings}

/**
 * Represents how to create a connection to a vehicle.
 *
 * SettingsFactory converts a configuration block into a settings object.
 * PropsFactory converts a settings object into actor properties.
 */
trait ConnectionFactory {
  def settings: SettingsFactory
  def props: PropsFactory

  def orElse(that: ConnectionFactory) = new ConnectionFactory {
    val settings = ConnectionFactory.this.settings orElse that.settings
    val props = ConnectionFactory.this.props orElse that.props
  }
}

object DefaultConnectionFactory extends ConnectionFactory {
  val settings: SettingsFactory = {
    case ConnectionConfig(UdpListenerSettings.prefix, config) => UdpListenerSettings(config)
    case ConnectionConfig(TcpClientSettings.prefix, config) => TcpClientSettings(config)
    case ConnectionConfig(SerialClientSettings.prefix, config) => SerialClientSettings(config)
  }

  val props: PropsFactory = {
    case (settings: UdpListenerSettings, sctx) => UdpListener.props(settings, sctx)
    case (settings: TcpClientSettings, sctx) => TcpClient.props(settings, sctx)
    case (settings: SerialClientSettings, sctx) => SerialClient.props(settings, sctx)
  }
}