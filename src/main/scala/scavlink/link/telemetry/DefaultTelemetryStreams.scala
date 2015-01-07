package scavlink.link.telemetry

import scavlink.state._

object DefaultTelemetryStreams {
  val system = SystemState
  val battery = BatteryState
  val gps = GpsState
  val control = ChannelState
  val location = LocationState
  val motion = MotionState
  val mission = MissionState
  val linkErrors = LinkErrorState

  val all = Set[StateGenerator[_ <: State]](system, battery, gps, control, location, motion, mission, linkErrors)
}
