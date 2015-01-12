package scavlink.state

import scavlink.coord.Geo
import scavlink.message.VehicleId
import scavlink.message.common.GpsRawInt
import scavlink.message.enums.MavDataStream

case class GpsState(vehicle: VehicleId,
                    timeIndex: Long = 0,
                    fixType: GpsFixType.Value = GpsFixType.Unknown,
                    rawLocation: Geo = Geo(),
                    groundspeed: Double = 0,
                    course: Double = 0,
                    satellitesVisible: Int = 0,
                    horizontalDop: Double = 0,
                    verticalDop: Double = 0) extends State {
  override def toString = s"GpsState(vehicle=$vehicle fixType=$fixType rawLocation=$rawLocation" +
    s" groundspeed=$groundspeed course=$course satellitesVisible=$satellitesVisible" +
    s" horizontalDop=$horizontalDop verticalDop=$verticalDop)"
}

object GpsFixType extends Enumeration {
  val Unknown = Value(0)
  val None = Value(1)
  val _2D = Value(2, "2D")
  val _3D = Value(3, "3D")
  val DGPS = Value(4)
  val RTK = Value(5)
}

object GpsState extends StateGenerator[GpsState] {
  def stateType = classOf[GpsState]
  def create = id => GpsState(id)
  def streams = Set(MavDataStream.EXTENDED_STATUS)
  def messages = Set(GpsRawInt())

  def extract = {
    case (state: GpsState, msg: GpsRawInt) =>
      state.copy(
        fixType = GpsFixType(msg.fixType),
        timeIndex = msg.timeUsec,
        rawLocation = Geo(
          msg.lat.toDouble / LatLonScale,
          msg.lon.toDouble / LatLonScale,
          msg.alt.toDouble / AltScale
        ),
        groundspeed = msg.vel.toDouble / 100,
        course = msg.cog.toDouble / 100,
        satellitesVisible = msg.satellitesVisible,
        horizontalDop = msg.eph.toDouble / 100,
        verticalDop = msg.epv.toDouble / 100
      )
  }
}