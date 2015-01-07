package scavlink.state

import scavlink.message.VehicleId
import scavlink.message.common.RcChannelsRaw
import scavlink.message.enums.MavDataStream

object Channel {
  val Roll = 1
  val Pitch = 2
  val Throttle = 3
  val Yaw = 4
}

/**
 * @param channels values ranged 0-100
 * @param signalStrength range 0-100 or -1 for unknown
 */
case class ChannelState(vehicle: VehicleId,
                        channels: Map[Int, Double] = Map.empty.withDefaultValue(-1),
                        signalStrength: Int = -1) extends State {
  val throttle = channels(Channel.Throttle)
  val roll = channels(Channel.Roll)
  val pitch = channels(Channel.Pitch)
  val yaw = channels(Channel.Yaw)

  override def toString: String = s"ChannelState(vehicle=$vehicle throttle=$throttle roll=$roll pitch=$pitch yaw=$yaw" +
    s" channels=$channels signalStrength=$signalStrength)"
}


object ChannelState extends StateGenerator[ChannelState] {
  def stateType = classOf[ChannelState]
  def create = id => ChannelState(id)
  def streams = Set(MavDataStream.RC_CHANNELS)
  def messages = Set(RcChannelsRaw())

  def extract = {
    case (state: ChannelState, msg: RcChannelsRaw) =>
      state.copy(
        signalStrength = msg.rssi,
        channels = updateChannels(state.channels, msg)
      )
  }

  def scale(v: Short): Double = (v - 1000).toDouble / 10

  def updateChannels(channels: Map[Int, Double], msg: RcChannelsRaw): Map[Int, Double] = {
    val n = msg.port * 8
    channels ++ Map[Int, Double](
      n + 1 -> scale(msg.chan1Raw),
      n + 2 -> scale(msg.chan2Raw),
      n + 3 -> scale(msg.chan3Raw),
      n + 4 -> scale(msg.chan4Raw),
      n + 5 -> scale(msg.chan5Raw),
      n + 6 -> scale(msg.chan6Raw),
      n + 7 -> scale(msg.chan7Raw),
      n + 8 -> scale(msg.chan8Raw)
    )
  }
}
