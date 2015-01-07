package scavlink.state

import scavlink.message.VehicleId
import scavlink.message.common.{Heartbeat, SysStatus, SystemTime}
import scavlink.message.enums._

case class SystemState(vehicle: VehicleId,
                       vehicleType: MavType.Value = MavType(0),
                       autopilot: MavAutopilot.Value = MavAutopilot(0),
                       systemState: MavState.Value = MavState(0),
                       systemModes: MavModeFlag.ValueSet = MavModeFlag.ValueSet.empty,
                       specificMode: Int = 0,
                       systemClock: Long = 0,
                       timeIndex: Long = 0,
                       sensors: Set[SensorState] = Set.empty) extends State {
  override def toString = s"SystemState(vehicle=$vehicle vehicleType=$vehicleType" +
    s" autopilot=$autopilot systemState=$systemState systemModes=${ systemModes.mkString(",") }" +
    s" specificMode=$specificMode clock=$systemClock timeIndex=$timeIndex sensors=$sensors)"
}

case class SensorState(sensor: MavSysStatusSensor.Value, isEnabled: Boolean, isHealthy: Boolean)

object SystemState extends StateGenerator[SystemState] {
  def stateType = classOf[SystemState]
  def create = id => SystemState(id)
  def streams = Set(MavDataStream.EXTENDED_STATUS, MavDataStream.EXTRA3)
  def messages = Set(Heartbeat(), SysStatus(), SystemTime())

  def extract = {
    case (state: SystemState, msg: Heartbeat) =>
      state.copy(
        vehicleType = MavType(msg.`type`),
        autopilot = MavAutopilot(msg.autopilot),
        systemState = MavState(msg.systemStatus),
        systemModes = MavModeFlag.maskToSet(msg.baseMode),
        specificMode = msg.customMode)

    case (state: SystemState, msg: SysStatus) =>
      state.copy(
        sensors = MavSysStatusSensor.maskToSet(msg.onboardControlSensorsPresent).map { sensor =>
          val isEnabled = (msg.onboardControlSensorsEnabled & sensor.id) > 0
          val isHealthy = (msg.onboardControlSensorsHealth & sensor.id) > 0
          SensorState(sensor, isEnabled, isHealthy)
        })

    case (state: SystemState, msg: SystemTime) =>
      state.copy(
        systemClock = msg.timeUnixUsec / 1000,
        timeIndex = msg.timeBootMs
      )
  }
}
