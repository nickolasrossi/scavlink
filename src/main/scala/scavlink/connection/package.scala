package scavlink

import akka.actor.Props

package object connection {
  type SettingsFactory = PartialFunction[ConnectionConfig, ConnectionSettings]
  type PropsFactory = PartialFunction[(ConnectionSettings, ScavlinkContext), Props]
}
