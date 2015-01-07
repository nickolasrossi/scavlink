package scavlink.state

import scavlink.coord.{Geo, NED}
import scavlink.message.VehicleId
import scavlink.message.common.{GlobalPositionInt, LocalPositionNed}
import scavlink.message.enums.MavDataStream

case class LocationState(vehicle: VehicleId,
                         timeIndex: Long = 0,
                         location: Geo = Geo(),
                         local: NED = NED(),
                         heading: Double = 0) extends State {
  override def toString = s"LocationState(vehicle=$vehicle location=$location heading=$heading time=$timeIndex)"
}

object LocationState extends StateGenerator[LocationState] {
  def stateType = classOf[LocationState]
  def create = id => LocationState(id)
  def streams = Set(MavDataStream.POSITION)
  def messages = Set(GlobalPositionInt(), LocalPositionNed())

  def extract: StateExtractor = {
    case (state: LocationState, msg: GlobalPositionInt) =>
      state.copy(
        timeIndex = msg.timeBootMs,
        location = Geo(
          msg.lat.toDouble / LatLonScale,
          msg.lon.toDouble / LatLonScale,
          msg.relativeAlt.toDouble / AltScale
        ),
        heading = msg.hdg.toDouble / 100
      )

    case (state: LocationState, msg: LocalPositionNed) =>
      state.copy(local = NED(msg.x, msg.y, msg.z))
  }
}