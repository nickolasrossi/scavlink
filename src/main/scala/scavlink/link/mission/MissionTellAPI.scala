package scavlink.link.mission

import akka.actor.{Actor, ActorRef}
import scavlink.link.Vehicle

/**
 * Fire-and-forget API for the mission cache.
 * Use "import MissionTellAPI._" to add methods to Vehicle object where needed.
 * @author Nick Rossi
 */
object MissionTellAPI {
  implicit class MissionAPI(val vehicle: Vehicle) {
    def getMission()(implicit sender: ActorRef = Actor.noSender): Unit =
      vehicle.missionCache ! GetMission()

    def setMission(mission: Mission)(implicit sender: ActorRef = Actor.noSender): Unit =
      vehicle.missionCache ! SetMission(mission)

    def clearMissionCache(): Unit = vehicle.missionCache ! ClearCache
  }
}
