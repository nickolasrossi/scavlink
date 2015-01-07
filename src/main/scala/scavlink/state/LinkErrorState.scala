package scavlink.state

import scavlink.message.VehicleId
import scavlink.message.common.SysStatus
import scavlink.message.enums.MavDataStream

case class LinkErrorState(vehicle: VehicleId,
                          droppedPackets: Int = 0,
                          packetErrors: Int = 0,
                          customCounts: (Int, Int, Int, Int) = (0, 0, 0, 0)) extends State {
  override def toString: String = s"LinkErrorState(vehicle=$vehicle droppedPackets=$droppedPackets" +
    s" packetErrors=$packetErrors customCounts=$customCounts)"
}

object LinkErrorState extends StateGenerator[LinkErrorState] {
  def stateType = classOf[LinkErrorState]
  def create = id => LinkErrorState(id)
  def streams = Set(MavDataStream.EXTENDED_STATUS)
  def messages = Set(SysStatus())

  def extract = {
    case (state: LinkErrorState, msg: SysStatus) =>
      state.copy(
        droppedPackets = msg.dropRateComm,
        packetErrors = msg.errorsComm,
        customCounts = (msg.errorsCount1, msg.errorsCount2, msg.errorsCount3, msg.errorsCount4))
  }
}