package scavlink.test.map

import scavlink.ScavlinkInstance
import scavlink.coord.Geo
import scavlink.link.Vehicle
import scavlink.link.channel.ChannelTellAPI._
import scavlink.link.mission._
import scavlink.link.mission.MissionAskAPI._
import scavlink.link.nav._
import scavlink.link.nav.NavAskAPI._
import scavlink.message.Command

import scala.concurrent.{ExecutionContext, Future}

trait Flight {
  def connectAll(scavlink: ScavlinkInstance): Unit
  def runFlight(vehicle: Vehicle)(implicit ec: ExecutionContext): Unit
  def rotorTakeoff: Vehicle => Future[RotorTakeoffResult] = vehicle => vehicle.rotorGentleTakeoff()
}

trait MissionValue {
  def mission: Vector[Command]
  def go(vehicle: Vehicle)(implicit ec: ExecutionContext): Future[RunMissionResult] = {
    val future = vehicle.runMissionCourse(mission, TrackMission(mission))
    future.onSuccess { case r =>
      println(s"\nMission succeeded: $r\n")
    }
    future.onFailure { case r: RunMissionFailed =>
      println(s"\nMission failed: ${ r.error } (${ r.message })")
    }
    future
  }
}

trait GuidedValue {
  def points: Seq[Geo]
  def go(vehicle: Vehicle)(implicit ec: ExecutionContext): Future[RunGuidedCourseResult] = {
    val future = vehicle.runGuidedCourse(GotoLocations(points))
    future.onSuccess { case r =>
      println(s"\nGuidedCourse succeeded: $r\n")
    }
    future.onFailure { case r: RunGuidedCourseFailed =>
      println(s"\nGuidedCourse failed: ${ r.error } (${ r.message })")
    }
    future
  }
}

trait MissionFlight extends Flight with MissionValue {
  def runFlight(vehicle: Vehicle)(implicit ec: ExecutionContext): Unit = {
    vehicle.setMission(mission) onSuccess {
      case _ =>
        vehicle.awaitGpsFix() onSuccess {
          case _ =>
            if (!vehicle.info.typeInfo.isRotor) go(vehicle)
            else rotorTakeoff(vehicle) onSuccess {
              case _ => go(vehicle)
            }
        }
    }
  }
}

trait MissionDrive extends Flight with MissionValue {
  def runFlight(vehicle: Vehicle)(implicit ec: ExecutionContext): Unit = {
    vehicle.setMission(mission) onSuccess {
      case _ =>
        vehicle.awaitGpsFix() onSuccess {
          case _ =>
            vehicle.setThrottle(50)
            go(vehicle)
        }
    }
  }
}

trait GuidedFlight extends Flight with GuidedValue {
  def runFlight(vehicle: Vehicle)(implicit ec: ExecutionContext): Unit = {
    vehicle.awaitGpsFix() onSuccess {
      case _ =>
        if (!vehicle.info.typeInfo.isRotor) {
          go(vehicle)
        } else {
          rotorTakeoff(vehicle) onSuccess {
            case _ => go(vehicle)
          }
        }
    }
  }
}

trait GuidedDrive extends Flight with GuidedValue {
  def runFlight(vehicle: Vehicle)(implicit ec: ExecutionContext) = {
    vehicle.awaitGpsFix() onSuccess {
      case _ =>
        vehicle.setThrottle(50)
        go(vehicle)
    }
  }
}
