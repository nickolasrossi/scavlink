package scavlink.link.nav

import akka.actor.{Actor, ActorRef}
import org.json4s.JsonAST.JNothing
import scavlink.coord.Geo
import scavlink.link.Vehicle
import scavlink.link.mission._
import scavlink.link.operation._
import scavlink.message.Mode
import scavlink.task._

import scala.concurrent.duration._

/**
 * Fire-and-forget API for navigation operations.
 * Use "import NavTellAPI._" to add these methods to Vehicle object where needed.
 * @author Nick Rossi
 */
object NavTellAPI {
  implicit class Nav(val vehicle: Vehicle) {
    /**
     * Wait for the vehicle to obtain a GPS fix.
     */
    def awaitGpsFix(minSatellites: Int = 1)
                   (implicit sender: ActorRef = Actor.noSender, flags: OpFlags = OpFlags(WithProgress)): Unit =
      vehicle.navigation !(flags, AwaitGpsFix(minSatellites))


    /**
     * Arm or disarm the motors.
     * @param shouldArm true to arm, false to disarm
     */
    def armMotors(shouldArm: Boolean)
                 (implicit sender: ActorRef = Actor.noSender, flags: OpFlags = OpFlags(WithProgress)): Unit =
      holdConversation(ArmMotors(shouldArm))

    /**
     * Set the vehicle mode.
     * @param mode vehicle-independent mode
     */
    def setMode(mode: Mode)
               (implicit sender: ActorRef = Actor.noSender, flags: OpFlags = OpFlags()): Unit =
      holdConversation(SetVehicleMode(vehicle, mode))

    /**
     * Set the vehicle mode and waits for heartbeat to reflect it.
     * @param mode vehicle-independent mode
     */
    def setModeToHeartbeat(mode: Mode)
                          (implicit sender: ActorRef = Actor.noSender, flags: OpFlags = OpFlags(WithProgress)): Unit =
      holdConversation(SetVehicleModeToHeartbeat(vehicle, mode))

    /**
     * Execute a takeoff from ground (rotorcraft only).
     */
    def rotorTakeoff(targetHeight: Double, startThrottle: Double, maxThrottle: Double, throttleRampRate: Double)
                    (implicit sender: ActorRef = Actor.noSender, flags: OpFlags = OpFlags(WithProgress)): Expect = {
      vehicle.navigation !(flags, RotorTakeoff(targetHeight, startThrottle, maxThrottle, throttleRampRate))
      ExpectTakeoff
    }

    /**
     * Use default settings for a gentle takeoff.
     */
    def rotorGentleTakeoff()(implicit sender: ActorRef = Actor.noSender, flags: OpFlags = OpFlags(WithProgress)): Expect = {
      vehicle.navigation !(flags, RotorTakeoff.Gentle)
      ExpectTakeoff
    }

    /**
     * Use default settings for a hard takeoff.
     */
    def rotorHardTakeoff()(implicit sender: ActorRef = Actor.noSender, flags: OpFlags = OpFlags(WithProgress)): Expect = {
      vehicle.navigation !(flags, RotorTakeoff.Hard)
      ExpectTakeoff
    }

    /**
     * Navigate the vehicle to a specific location.
     * @param location destination
     * @param maxEta cancel course if ETA calculation exceeds this many seconds
     * @param smoothingWindow number of seconds to average out ETA calculation
     */
    def gotoLocation(location: Geo, maxEta: Long = 6.hours.toMinutes, smoothingWindow: Int = 10)
                    (implicit sender: ActorRef = Actor.noSender, flags: OpFlags = OpFlags(WithProgress)): Expect = {
      runGuidedCourse(GotoLocations(
        Vector(location),
        maxEta = maxEta.seconds,
        smoothingWindow = smoothingWindow.seconds
      ))
      ExpectGoto
    }

    /**
     * Navigate the vehicle to a series of locations.
     * @param locations destinations
     * @param maxEta cancel course if ETA calculation exceeds this many seconds
     * @param smoothingWindow number of seconds to average out ETA calculation
     */
    def gotoLocations(locations: Vector[Geo], maxEta: Long = 6.hours.toSeconds, smoothingWindow: Int = 10)
                     (implicit sender: ActorRef = Actor.noSender, flags: OpFlags = OpFlags(WithProgress)): Expect = {
      runGuidedCourse(GotoLocations(
        locations,
        maxEta = maxEta.seconds,
        smoothingWindow = smoothingWindow.seconds
      ))
      ExpectGoto
    }

    /**
     * Navigate the specified course.
     */
    def runGuidedCourse(course: GuidedCourse)
                       (implicit sender: ActorRef = Actor.noSender, flags: OpFlags = OpFlags(WithProgress)): Expect = {
      vehicle.navigation !(flags, RunGuidedCourse(course))
      ExpectGoto
    }

    /**
     * Navigate the specified mission.
     */
    def runMission(mission: Mission)
                  (implicit sender: ActorRef = Actor.noSender, flags: OpFlags = OpFlags(WithProgress)): Expect = {
      runMissionCourse(mission, TrackMission(mission))
      ExpectMission
    }

    /**
     * Navigate the specified mission, using the specified course to track progress.
     */
    def runMissionCourse(mission: Mission, course: MissionCourse)
                        (implicit sender: ActorRef = Actor.noSender, flags: OpFlags = OpFlags(WithProgress)): Expect = {
      vehicle.navigation !(flags, RunMission(mission, course))
      ExpectMission
    }

    /**
     * Execute a packet conversation with the vehicle.
     */
    def holdConversation(conversation: Conversation)
                        (implicit sender: ActorRef, flags: OpFlags = OpFlags(WithProgress)): Unit =
      vehicle.navigation !(flags, conversation)
  }


  case object ExpectTakeoff extends ExpectOpFailure {
    def opFailure = {
      case fail: RotorTakeoffFailed => TaskComplete.failed(fail.error.toString + ": " + fail.message)
    }
  }


  case object ExpectGoto extends ExpectOp {
    def dataFor(last: GotoLocations): Map[String, Any] = {
      var data = Map("index" -> last.index, "waypoint" -> last.waypoint, "distance" -> last.distance)
      if (last.eta.isFinite()) data += ("eta" -> last.eta.toSeconds)
      data
    }

    def opFailure = {
      case RunGuidedCourseFailed(_, _, last: GotoLocations, error, msg) =>
        TaskComplete(false, error.toString + ": " + msg, dataFor(last))
    }

    def opResult = PartialFunction.empty

    def opProgress = {
      case RunGuidedCourseStatus(_, RunGuidedCourse(orig: GotoLocations), last: GotoLocations, _) =>
        val progress = ((last.index.toFloat / orig.locations.length) * 100).toInt
        TaskProgress(progress, "in transit", dataFor(last))
    }
  }


  case object ExpectMission extends ExpectOp {
    def dataFor(last: TrackMission): Map[String, Any] = {
      var data = Map("index" -> last.index, "waypoint" -> last.waypoint.getOrElse(JNothing), "distance" -> last.distance)
      if (last.eta.isFinite()) data += ("eta" -> last.eta.toSeconds)
      data
    }

    def opFailure = {
      case RunMissionFailed(_, _, last: TrackMission, error, msg) =>
        TaskComplete(false, error.toString + ": " + msg, dataFor(last))
    }

    def opResult = PartialFunction.empty

    def opProgress = {
      case RunMissionStatus(_, RunMission(mission, orig: TrackMission), last: TrackMission, _) =>
        val progress = ((last.index.toFloat / mission.length) * 100).toInt
        TaskProgress(progress, "in transit", dataFor(last))
    }
  }
}
