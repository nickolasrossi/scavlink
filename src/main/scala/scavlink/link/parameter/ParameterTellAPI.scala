package scavlink.link.parameter

import akka.actor.{Actor, ActorRef}
import scavlink.link.Vehicle

/**
 * Fire-and-forget API for parameter operations.
 * Use "import ParameterTellAPI._" to add these methods to Vehicle object where needed.
 * @author Nick Rossi
 */
object ParameterTellAPI {
  implicit class ParameterAPI(val vehicle: Vehicle) {
    def getAllParameters()(implicit sender: ActorRef = Actor.noSender): Unit =
      vehicle.parameterCache ! GetAllParameters()

    def getNamedParameters(names: Set[String])(implicit sender: ActorRef = Actor.noSender): Unit =
      vehicle.parameterCache ! GetNamedParameters(names)

    def setParameters(params: Map[String, AnyVal])(implicit sender: ActorRef = Actor.noSender): Unit =
      vehicle.parameterCache ! SetParameters(params)

    def clearParameterCache(): Unit = vehicle.parameterCache ! ClearCache
  }
}
