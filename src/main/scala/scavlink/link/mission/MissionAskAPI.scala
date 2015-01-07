package scavlink.link.mission

import akka.pattern.ask
import scavlink.link.Vehicle

import scala.concurrent.Future

/**
 * Futures-based mission API using the ask pattern.
 * Use "import MissionAskAPI._" to add methods to Vehicle object where needed.
 * Note: From inside an actor, the tell API is preferable.
 * @author Nick Rossi
 */
object MissionAskAPI {
  implicit class MissionAPI(val vehicle: Vehicle) {
    implicit val timeout = vehicle.settings.apiTimeout

    def getMission: Future[GetMissionResult] =
      (vehicle.missionCache ? GetMission()).mapTo[GetMissionResult]

    def setMission(mission: Mission): Future[SetMissionResult] =
      (vehicle.missionCache ? SetMission(mission)).mapTo[SetMissionResult]

    def clearMissionCache(): Unit = vehicle.missionCache ! ClearCache
  }
}