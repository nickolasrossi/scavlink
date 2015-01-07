package scavlink.connection

import com.typesafe.config.Config

case class ConnectionConfig(connectionType: String, config: Config)

trait ConnectionSettings {
  def actorName: String
  override def toString = actorName
}
