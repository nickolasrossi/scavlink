package scavlink.connection.serial

import com.typesafe.config.Config
import scavlink.connection.ConnectionSettings
import scavlink.settings.SettingsCompanion

import scala.concurrent.duration.FiniteDuration

case class SerialClientSettings(address: String, options: Options,
                                connectTimeout: FiniteDuration, reconnectInterval: FiniteDuration)
  extends ConnectionSettings {
  val actorName = address.replace('/', '_')
}

object SerialClientSettings extends SettingsCompanion[SerialClientSettings]("serial") {
  override def apply(config: Config): SerialClientSettings = {
    val bitrate = config.getInt("bitrate")

    // shorthand "8N1", "7E1", etc.
    val opts = config.getString("opts")
    val bits = DataBits.withName(opts(0).toString)
    val parity = Parity.withName(opts(1).toString.toUpperCase)
    val stops = StopBits.withName(opts.substring(2))

    SerialClientSettings(
      config.getString("address"),
      Options(bitrate, bits, parity, stops),
      getDuration(config, "connect-timeout"),
      getDuration(config, "reconnect-interval")
    )
  }

  def fromSubConfig(config: Config): SerialClientSettings = apply(config)
}
