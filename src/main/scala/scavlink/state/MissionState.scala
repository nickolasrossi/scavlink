package scavlink.state

import scavlink.message.VehicleId
import scavlink.message.common.{MissionCurrent, NavControllerOutput}
import scavlink.message.enums.MavDataStream

case class MissionState(vehicle: VehicleId,
                        currentIndex: Int = 0,
                        distanceToTarget: Double = 0,
                        bearingToTarget: Double = 0,
                        navRoll: Double = 0,
                        navPitch: Double = 0,
                        navBearing: Double = 0,
                        altitudeError: Double = 0,
                        airspeedError: Double = 0,
                        crosstrackError: Double = 0) extends State {
  override def toString = s"MissionState(vehicle=$vehicle currentIndex=$currentIndex" +
    s" distanceToTarget=$distanceToTarget bearingToTarget=$bearingToTarget" +
    s" navRoll=$navRoll navPitch=$navPitch navBearing=$navBearing" +
    s" altitudeError=$altitudeError airspeedError=$airspeedError crosstrackError=$crosstrackError)"
}

object MissionState extends StateGenerator[MissionState] {
  def stateType = classOf[MissionState]
  def create = id => MissionState(id)
  def streams = Set(MavDataStream.EXTENDED_STATUS)
  def messages = Set(MissionCurrent(), NavControllerOutput())

  def round4 = scavlink.coord.round(10000) _

  def extract = {
    case (state: MissionState, msg: NavControllerOutput) =>
      state.copy(
        distanceToTarget = msg.wpDist,
        bearingToTarget = msg.targetBearing,
        navRoll = round4(msg.navRoll),
        navPitch = round4(msg.navPitch),
        navBearing = msg.navBearing,
        altitudeError = round4(msg.altError),
        airspeedError = round4(msg.aspdError),
        crosstrackError = round4(msg.xtrackError))

    case (state: MissionState, msg: MissionCurrent) =>
      state.copy(currentIndex = msg.seq)
  }
}