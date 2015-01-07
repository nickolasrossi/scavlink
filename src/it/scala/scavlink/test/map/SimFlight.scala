package scavlink.test.map

import java.net.InetSocketAddress

import scavlink.ScavlinkInstance
import scavlink.connection.tcp.TcpClientSettings
import scavlink.coord.{ENU, Geo}
import scavlink.link.Vehicle
import scavlink.link.channel.ChannelTellAPI._
import scavlink.link.mission.MissionAskAPI._
import scavlink.link.mission._
import scavlink.link.nav.NavAskAPI._
import scavlink.link.nav.{AreaCoverage, RotorTakeoffResult}
import scavlink.message.{Mode, Command}
import scavlink.message.common._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

trait SimFlight extends Flight {
  def connectAll(scavlink: ScavlinkInstance) = {
    scavlink.startConnection(TcpClientSettings(new InetSocketAddress("127.0.0.1", 5760), 10.seconds, 10.seconds))
//    Timer(30000, false) {
//      scavlink.startConnection(TcpClientSettings(new InetSocketAddress("127.0.0.1", 5770), 10.seconds, 10.seconds))
//    }
  }

  override def rotorTakeoff: (Vehicle) => Future[RotorTakeoffResult] = api => api.rotorTakeoff(2, 40, 70, 10)
}

object SimFlight {
  val Nothing = new SimFlight {
    def runFlight(vehicle: Vehicle)(implicit ec: ExecutionContext): Unit = {}
  }

  val sunnyvaleBaylandsWaypoints = Vector[Geo](
    Geo(37.411848, -121.995104, 20),
    Geo(37.415248, -121.994568, 20),
    Geo(37.414762, -121.989364, 20),
    Geo(37.413697, -121.987712, 20),
    Geo(37.412103, -121.989493, 20),
    Geo(37.412572, -121.991489, 20),
    Geo(37.411848, -121.995104, 20)
  )

  val sunnyvaleBaylandsMission =
    sunnyvaleBaylandsWaypoints.map(NavWaypoint.apply) :+ NavReturnToLaunch()


  val SunnyvaleCopters = new SimFlight {
    def runFlight(vehicle: Vehicle)(implicit context: ExecutionContext): Unit = {
      for {
        _ <- vehicle.setMission(sunnyvaleBaylandsMission)
        _ <- vehicle.armMotors(true)
        _ <- vehicle.setMode(Mode.Auto)
      } {
        vehicle.setThrottle(50)
      }
    }
  }

  val SunnyvaleTransects = new MissionFlight with SimFlight {
    def mission: Vector[Command] = {
      val coverage = new AreaCoverage(sunnyvaleBaylandsWaypoints.map(_.latlon), 50)
      val points = coverage.transects(sunnyvaleBaylandsWaypoints(0).latlon, 50, true)
      sunnyvaleBaylandsMission(0) +: points.map(NavWaypoint.apply) :+ NavReturnToLaunch()
    }
  }


  val SunnyvaleGotoLocations = new GuidedFlight with SimFlight {
    /*    lazy val points: Stream[Geo] = {
          def loop(a: Geo, b: Geo): Stream[Geo] = a #:: loop(b, a)
          loop(greenLakeWaypoints_DoNotFlyInRealLife(0), greenLakeWaypoints_DoNotFlyInRealLife(2))
        }*/

    val points = List(
      sunnyvaleBaylandsWaypoints(2),
      sunnyvaleBaylandsWaypoints(5),
      sunnyvaleBaylandsWaypoints(3)
    )

    val fence = None
  }

  val SunnyvaleDriveToLocations = new GuidedDrive with SimFlight {
    val points = List(
      sunnyvaleBaylandsWaypoints(2),
      sunnyvaleBaylandsWaypoints(5),
      sunnyvaleBaylandsWaypoints(3)
    )
  }

  val SunnyvaleDiamond = new MissionFlight with SimFlight {
    val mission = {
      val start = Geo(37.412497, -121.995356, 20)
      val n = 200

      Vector[Command](
        NavWaypoint(start),
        NavWaypoint(start + ENU(n, 0, 0)),
        NavWaypoint(start + ENU(0, n, 0)),
        NavWaypoint(start + ENU(-n, 0, 0)),
        NavWaypoint(start + ENU(0, -n, 0)),
        NavWaypoint(start)
      )
    }
  }

  val SunnyvalePolygon = new PolygonMission(Geo(37.411689, -121.993900, 6), 10, 5) with MissionFlight with SimFlight
  val SunnyvaleGuidedPolygon = new PolygonGuided(Geo(37.411720, -121.994190, 6), 10, 5) with GuidedFlight with SimFlight
  val SunnyvaleCorkscrew = new CorkscrewMission(Geo(37.411858, -121.994250, 0)) with MissionFlight with SimFlight
  val SunnyvaleDriveCorkscrew = new CorkscrewMission(Geo(37.411858, -121.994250, 0)) with MissionDrive with SimFlight
}
