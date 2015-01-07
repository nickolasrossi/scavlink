package scavlink.link.parameter

import scavlink.link._
import scavlink.link.operation._
import scavlink.message.{From, Packet}
import scavlink.message.common.{ParamRequestRead, ParamValue}

case class GetNamedParameters(names: Set[String]) extends ParameterOp {
  require(names.size > 0)
  val actorType = classOf[GetNamedParametersActor]
}

case class GetNamedParametersResult(vehicle: Vehicle, op: GetNamedParameters, params: Parameters) extends ParameterOpResult {
  val isAll = false
}

/**
 * Retrieves named parameters from the vehicle.
 * @author Nick Rossi
 */
class GetNamedParametersActor(vehicle: Vehicle) extends VehicleOpActor[GetNamedParameters](vehicle) {

  private case class ParamData(received: Parameters, tries: Int = 0) extends ActionRetry with DefaultRetrySettings {
    def action() = if (tries > 0) requestNames(op.names.diff(received.keySet))
    def increment(): ActionRetry = copy(tries = tries + 1)
    override def timeoutResult: OpResult = GetNamedParametersResult(vehicle, op, received)
  }

  val matcher = SubscribeTo.complete {
    case Packet(From(`id`, _, _), msg: ParamValue) if msg.paramId != "" && msg.paramIndex == -1 => true
  }

  def requestNames(names: Set[String]) =
    names foreach { name =>
      link.send(ParamRequestRead(targetSystem, targetComponent, name, -1))
    }


  // FSM states

  when(Idle) {
    case Event(op: GetNamedParameters, Uninitialized) =>
      start(op, sender())
      link.events.subscribe(self, matcher)
      requestNames(op.names)
      goto(Active) using ParamData(Map.empty)
  }

  when(Active) {
    case Event(Packet(_, msg: ParamValue), ParamData(received, _)) =>
      val updated = received + (msg.paramId -> msg.paramValue)
      if (updated.size < op.names.size) {
        stay using ParamData(updated)
      } else {
        stop using Finish(GetNamedParametersResult(vehicle, op, updated))
      }
  }
}
