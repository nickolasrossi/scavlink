package scavlink

import scavlink.coord.{Coordinates, ENU, Geo, NED}
import scavlink.message.common.MissionItem
import scavlink.message.enums.MavFrame._
import scavlink.message.enums.{MavCmd, MavComponent, MavFrame}

import scala.language.implicitConversions
import scala.util.Try

package object message {

  implicit def systemId2Int(systemId: SystemId): Int = systemId.id
  implicit def componentId2Int(componentId: ComponentId): Int = componentId.id
  implicit def int2systemId(id: Int): SystemId = SystemId(id)
  implicit def int2componentId(id: Int): ComponentId = ComponentId(id)
  implicit def enum2componentId(enum: MavComponent.Value): ComponentId = ComponentId(enum)

  implicit def systemId2Byte(systemId: SystemId): Byte = systemId.toByte
  implicit def componentId2Byte(componentId: ComponentId): Byte = componentId.toByte
  implicit def byte2systemId(id: Byte): SystemId = SystemId(id)
  implicit def byte2componentId(id: Byte): ComponentId = ComponentId(id)

  def missionItemCoordinates(item: MissionItem): Option[Coordinates] = {
    if (item.command < MavCmd.NAV_LAST.id) {
      MavFrame(item.frame) match {
        case LOCAL_NED | LOCAL_OFFSET_NED | BODY_NED | BODY_OFFSET_NED => Try(NED(item.x, item.y, item.z)).toOption
        case LOCAL_ENU => Try(ENU(item.x, item.y, item.z)).toOption
        case GLOBAL | GLOBAL_RELATIVE_ALT | GLOBAL_TERRAIN_ALT if item.x != 0 || item.y != 0 || item.z != 0 => Try(Geo(item.x, item.y, item.z)).toOption
        case _ => None
      }
    } else {
      None
    }
  }
}
