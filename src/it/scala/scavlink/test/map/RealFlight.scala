package scavlink.test.map

import scavlink.ScavlinkInstance
import scavlink.connection.serial.{Serial, SerialClientSettings}
import scavlink.coord.Geo
import scavlink.message.Command
import scavlink.message.common.NavWaypoint

import scala.concurrent.duration._

trait RealFlight extends Flight {
  def connectAll(mavlink: ScavlinkInstance): Unit = {
    mavlink.startConnection(SerialClientSettings(
      "/dev/tty.usbserial-DN006OQW", Serial.DefaultOptions, 2.seconds, 10.seconds))
  }
}

object RealFlight {
  val SunnyvalePolygon = new PolygonMission(Geo(37.411870, -121.993950, 6), 8, 8) with MissionFlight with RealFlight
  val SunnyvaleGuidedPolygon = new PolygonGuided(Geo(37.411870, -121.993950, 6), 8, 8) with GuidedFlight with RealFlight

  val SunnyvalBackAndForth = new GuidedFlight with RealFlight {
    val start = Geo(37.411730, -121.993850, 4)
    val end = Geo(37.411730, -121.993950, 4)

    val points: Seq[Geo] = Vector.tabulate(20) { i =>
      if (i % 2 == 0) start else end
    }
  }

  val SunnyvaleCorkscrew = new CorkscrewMission(Geo(37.411870, -121.994150, 4)) with MissionFlight with RealFlight

  val SunnyvaleTest = new MissionFlight with RealFlight {
    def mission: Vector[Command] = {
      val points = Polygon.make(Geo(37.411689, -121.993790, 6), 10, 5)
      val first = NavWaypoint(location = points.head)
      first +: points.map(NavWaypoint.apply)
    }
  }
}
