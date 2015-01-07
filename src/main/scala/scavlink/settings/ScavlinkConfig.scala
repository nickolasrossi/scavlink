package scavlink.settings

import com.typesafe.config._
import scavlink.connection.{HeartbeatSettings, ConnectionConfig}

import scala.collection.JavaConversions._

/**
 * MAVLink library settings.
 * An application must initialize the singleton with its config object before using the library.
 * Otherwise, it will load the default config from ConfigFactory and use that.
 */
class ScavlinkConfig(systemConfig: Config, name: String = "scavlink") {
  systemConfig.checkValid(ConfigFactory.defaultReference(), name)

  val root = systemConfig.getConfig(name)

  val connections = root.getConfigList("connections").toList.map {
    node => ConnectionConfig(node.getString("type"), node)
  }

  val heartbeat = HeartbeatSettings(root)
}
