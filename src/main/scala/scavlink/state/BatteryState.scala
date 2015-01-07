package scavlink.state

import scavlink.message.VehicleId
import scavlink.message.common.SysStatus
import scavlink.message.enums.MavDataStream

case class BatteryState(vehicle: VehicleId,
                        remaining: Int = 0,
                        voltage: Float = 0,
                        current: Float = 0) extends State {
  override def toString: String = s"BatteryState(vehicle=$vehicle remaining=$remaining voltage=$voltage current=$current)"
}

object BatteryState extends StateGenerator[BatteryState] {
  def stateType = classOf[BatteryState]
  def create = id => BatteryState(id)
  def streams = Set(MavDataStream.EXTENDED_STATUS)
  def messages = Set(SysStatus())

  def extract = {
    case (state: BatteryState, msg: SysStatus) =>
      state.copy(
        remaining = msg.batteryRemaining,
        voltage = msg.voltageBattery.toFloat / 1000,
        current = if (msg.currentBattery >= 0) msg.currentBattery.toFloat / 100 else 0
      )
  }
}