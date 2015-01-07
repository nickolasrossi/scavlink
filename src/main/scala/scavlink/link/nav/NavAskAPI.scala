package scavlink.link.nav

import akka.pattern.ask
import scavlink.coord.Geo
import scavlink.link.Vehicle
import scavlink.link.mission._
import scavlink.link.operation.{Conversation, ConversationSucceeded}
import scavlink.message
import scavlink.message.Mode

import scala.concurrent.Future

/**
 * Futures-based API for navigation operations.
 * Use "import NavAskAPI._" to add methods to Vehicle object where needed.
 * Better yet, use the tell API instead (NavTellAPI).
 * Akka ask is problematic in production settings; it's only included here for ad-hoc testing.
 *
 * Note: OpFlags are not allowed for ask APIs, because they can change the
 * structure of result messages in ways that the future's private actor won't recognize.
 *
 * @author Nick Rossi
 */
object NavAskAPI {
  implicit class Nav(val vehicle: Vehicle) {
    implicit val timeout = vehicle.settings.apiTimeout

    /**
     * Wait for the vehicle to obtain a GPS fix.
     */
    def awaitGpsFix(minSatellites: Int = 1): Future[AwaitGpsFixResult] =
      (vehicle.navigation ? AwaitGpsFix(minSatellites)).mapTo[AwaitGpsFixResult]

    /**
     * Arm or disarm the vehicle.
     * @param shouldArm true to arm, false to disarm
     */
    def armMotors(shouldArm: Boolean): Future[ConversationSucceeded] = holdConversation(ArmMotors(shouldArm))

    /**
     * Set the vehicle mode.
     * @param mode vehicle-independent mode (see [[message.Mode]])
     */
    def setMode(mode: Mode): Future[ConversationSucceeded] = holdConversation(SetVehicleMode(vehicle, mode))

    /**
     * Set the vehicle mode and waits for heartbeat to reflect it.
     * @param mode vehicle-independent mode (see [[message.Mode]])
     */
    def setModeToHeartbeat(mode: Mode): Future[ConversationSucceeded] = holdConversation(SetVehicleModeToHeartbeat(vehicle, mode))

    /**
     * Execute a takeoff from ground (rotorcraft only).
     */
    def rotorTakeoff(targetHeight: Double, 
                     startThrottle: Double, maxThrottle: Double, throttleRampRate: Double): Future[RotorTakeoffResult] =
      (vehicle.navigation ? RotorTakeoff(targetHeight, startThrottle, maxThrottle, throttleRampRate)).mapTo[RotorTakeoffResult]

    /**
     * Use default settings for a gentle takeoff.
     */
    def rotorGentleTakeoff(): Future[RotorTakeoffResult] =
      (vehicle.navigation ? RotorTakeoff.Gentle).mapTo[RotorTakeoffResult]

    /**
     * Move the vehicle along the specified course.
     */
    def runGuidedCourse(course: GuidedCourse): Future[RunGuidedCourseResult] =
      (vehicle.navigation ? RunGuidedCourse(course)).mapTo[RunGuidedCourseResult]

    /**
     * Move the vehicle to a specific location.
     */
    def gotoLocation(location: Geo) = runGuidedCourse(GotoLocations(Vector(location)))

    /**
     * Move the vehicle to a series of locations.
     * @param locations destinations
     */
    def gotoLocations(locations: Seq[Geo]) = runGuidedCourse(GotoLocations(locations))
    
    /**
     * Send the vehicle on the specified mission.
     */
    def runMission(mission: Mission): Future[RunMissionResult] =
      runMissionCourse(mission, TrackMission(mission))

    /**
     * Send the vehicle on the specified mission, using the specified course to track progress.
     */
    def runMissionCourse(mission: Mission, course: MissionCourse): Future[RunMissionResult] =
      (vehicle.navigation ? RunMission(mission, course)).mapTo[RunMissionResult]

    /**
     * Execute a conversation with the vehicle.
     */
    def holdConversation(conversation: Conversation): Future[ConversationSucceeded] =
      (vehicle.navigation ? conversation).mapTo[ConversationSucceeded]
  }
}
