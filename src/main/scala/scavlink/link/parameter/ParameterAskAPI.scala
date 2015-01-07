package scavlink.link.parameter

import akka.pattern.ask
import scavlink.link.Vehicle

import scala.concurrent.Future

/**
 * Futures-based parameter API using the ask pattern.
 * Note: From inside an actor, it's preferable to send messages with "tell" rather than "ask".
 */
object ParameterAskAPI {
  implicit class ParameterAPI(val vehicle: Vehicle) {
    implicit val timeout = vehicle.settings.apiTimeout

    def getAllParameters: Future[GetAllParametersResult] =
      (vehicle.parameterCache ? GetAllParameters()).mapTo[GetAllParametersResult]

    def getNamedParameters(names: Set[String]): Future[GetNamedParametersResult] =
      (vehicle.parameterCache ? GetNamedParameters(names)).mapTo[GetNamedParametersResult]

    def setParameters(params: Map[String, AnyVal]): Future[SetParametersResult] =
      (vehicle.parameterCache ? SetParameters(params)).mapTo[SetParametersResult]

    def clearParameterCache(): Unit = vehicle.parameterCache ! ClearCache
  }
}