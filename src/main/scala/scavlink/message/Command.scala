package scavlink.message

import scavlink.coord._
import scavlink.message.common.{CommandLong, MissionItem, MissionItemInt}
import scavlink.message.enums.{MavCmd, MavFrame}

trait Command {
  def targetSystem: SystemId
  def targetComponent: ComponentId
  def cmd: MavCmd.Value
  def message: CommandLong

  def toMissionItem(index: Int, autocontinue: Boolean = true, current: Int = 0): MissionItem = {
    MissionItem(
      targetSystem = message.targetSystem,
      targetComponent = message.targetComponent,
      seq = index.toShort,
      frame = frame.id.toByte,
      current = current.toByte,
      autocontinue = if (autocontinue) 1 else 0,
      command = message.command.id.toShort,
      param1 = message.param1,
      param2 = message.param2,
      param3 = message.param3,
      param4 = message.param4,
      x = message.param5,
      y = message.param6,
      z = message.param7
    )
  }

  def toMissionItemInt(index: Int, autocontinue: Boolean = true, current: Int = 0): MissionItemInt = {
    val (scaledX, scaledY, scaledZ) = this match {
      case c: Location[_] => scale(c.location)
      case _ => (0, 0, 0)
    }

    MissionItemInt(
      targetSystem = message.targetSystem,
      targetComponent = message.targetComponent,
      seq = index.toShort,
      frame = frame.id.toByte,
      current = current.toByte,
      autocontinue = if (autocontinue) 1 else 0,
      command = message.command.id.toShort,
      param1 = message.param1,
      param2 = message.param2,
      param3 = message.param3,
      param4 = message.param4,
      x = scaledX,
      y = scaledY,
      z = scaledZ
    )
  }

  def frame: MavFrame.Value = this match {
    case c: Location[_] =>
      c.location match {
        case _: Geo => MavFrame.GLOBAL_RELATIVE_ALT
        case _: NED => MavFrame.LOCAL_NED
        case _: ENU => MavFrame.LOCAL_ENU
      }

    case _ => MavFrame.MISSION
  }

  private def scale(location: Coordinates): (Int, Int, Int) = location match {
    case loc: Geo => ((loc.x * LatLonScale).toInt, (loc.y * LatLonScale).toInt, (loc.z * AltScale).toInt)
    case loc => ((loc.x * AltScale).toInt, (loc.y * AltScale).toInt, (loc.z * AltScale).toInt)
  }
}

trait Location[T <: Command] {
  def location: Coordinates
  def setLocation(location: Coordinates): T
}
