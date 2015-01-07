package scavlink.task

import com.typesafe.config.Config
import scavlink.settings.SettingsCompanion

import scala.collection.JavaConversions._
import scala.concurrent.duration.FiniteDuration

case class TaskSettings(apis: List[String],
                        service: ServiceSettings,
                        clients: Set[ClientSettings],
                        ssl: SslSettings)

object TaskSettings extends SettingsCompanion[TaskSettings]("task") {
  def fromSubConfig(config: Config): TaskSettings = {
    val clients = config.getConfigList("clients").toList.map(ClientSettings.fromSubConfig)

    TaskSettings(
      config.getStringList("apis").toList,
      ServiceSettings(config),
      clients.toSet,
      SslSettings(config)
    )
  }
}


case class ServiceSettings(isEnabled: Boolean, interface: String, port: Int, tokenIdleTimeout: FiniteDuration)

object ServiceSettings extends SettingsCompanion[ServiceSettings]("service") {
  def fromSubConfig(config: Config): ServiceSettings = ServiceSettings(
    config.getBoolean("enabled"),
    config.getString("interface"),
    config.getInt("port"),
    getDuration(config, "token-idle-timeout")
  )
}


case class ClientSettings(isEnabled: Boolean,
                          isSecure: Boolean,
                          host: String,
                          port: Int,
                          username: String,
                          password: String)

object ClientSettings extends SettingsCompanion[ClientSettings]("client") {
  def fromSubConfig(config: Config): ClientSettings =
    ClientSettings(
      config.getBoolean("enabled"),
      config.getBoolean("secure"),
      config.getString("host"),
      config.getInt("port"),
      config.getString("username"),
      config.getString("password")
    )
}
