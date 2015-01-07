package scavlink.state

import scavlink.coord.Vector3
import scavlink.message.VehicleId
import scavlink.message.common.{Attitude, GlobalPositionInt, GpsRawInt, VfrHud}
import scavlink.message.enums.MavDataStream

/**
 * @param airspeed in meters/sec
 * @param groundspeed in meters/sec
 * @param heading in degrees
 * @param courseOverGround in degrees
 * @param climb in meters/sec
 * @param roll in radians
 * @param pitch in radians
 * @param yaw in radians
 * @param rollSpeed in radians/sec
 * @param pitchSpeed in radians/sec
 * @param yawSpeed in radians/sec
 */
case class MotionState(vehicle: VehicleId,
                       timeIndex: Long = 0,
                       airspeed: Double = 0,
                       groundspeed: Double = 0,
                       groundspeedVector: Vector3 = Vector3(),
                       heading: Double = 0,
                       courseOverGround: Double = 0,
                       throttle: Int = 0,
                       climb: Double = 0,
                       roll: Double = 0,
                       pitch: Double = 0,
                       yaw: Double = 0,
                       rollSpeed: Double = 0,
                       pitchSpeed: Double = 0,
                       yawSpeed: Double = 0) extends State {
  override def toString = s"MotionState(vehicle=$vehicle time=$timeIndex airspeed=$airspeed groundspeed=$groundspeed" +
    s" groundspeedVector=$groundspeedVector heading=$heading courseOverGround=$courseOverGround throttle=$throttle" +
    s" climb=$climb roll=$roll pitch=$pitch yaw=$yaw rollSpeed=$rollSpeed pitchSpeed=$pitchSpeed yawSpeed=$yawSpeed)"

}

object MotionState extends StateGenerator[MotionState] {
  def stateType = classOf[MotionState]
  def create = id => MotionState(id)
  def streams = Set(MavDataStream.EXTENDED_STATUS, MavDataStream.EXTRA1, MavDataStream.EXTRA2)
  def messages = Set(GlobalPositionInt(), GpsRawInt(), Attitude(), VfrHud())

  def round4 = scavlink.coord.round(10000) _

  def extract = {
    case (state: MotionState, msg: VfrHud) =>
      state.copy(
        groundspeed = round4(msg.groundspeed),
        airspeed = round4(msg.airspeed),
        heading = msg.heading,
        throttle = msg.throttle,
        climb = round4(msg.climb)
      )

    case (state: MotionState, msg: Attitude) =>
      state.copy(
        timeIndex = msg.timeBootMs,
        roll = round4(msg.roll),
        pitch = round4(msg.pitch),
        yaw = round4(msg.yaw),
        rollSpeed = round4(msg.rollspeed),
        pitchSpeed = round4(msg.pitchspeed),
        yawSpeed = round4(msg.yawspeed)
      )

    case (state: MotionState, msg: GlobalPositionInt) =>
      state.copy(
        groundspeedVector = Vector3(
          msg.vx.toDouble / 100,
          msg.vy.toDouble / 100,
          msg.vz.toDouble / 100
        )
      )

    case (state: MotionState, msg: GpsRawInt) =>
      state.copy(courseOverGround = msg.cog.toDouble / 100)
  }
}