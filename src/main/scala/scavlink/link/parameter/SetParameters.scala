package scavlink.link.parameter

import scavlink.link._
import scavlink.link.operation._
import scavlink.message.{From, Packet}
import scavlink.message.common.{ParamSet, ParamValue}

/**
 * @param params map of parameter name to numeric value
 */
case class SetParameters(params: Map[String, AnyVal]) extends ParameterOp {
  require(params.size > 0 && params.size <= maxParameterCount)
  require(params.keySet.forall(_.length < maxParameterNameLength))

  val actorType = classOf[SetParametersActor]
  val paramNames = params.keySet
}

case class SetParametersResult(vehicle: Vehicle, op: SetParameters, params: Parameters) extends ParameterOpResult {
  val isAll = false
}

case class SetParametersPartialFailure(vehicle: Vehicle, op: SetParameters, params: Parameters, notSet: Map[String, AnyVal])
  extends OpException with ParameterOpResult {
  val isAll = false
}

/**
 * Sets parameter values on the vehicle.
 * @author Nick Rossi
 */
class SetParametersActor(vehicle: Vehicle) extends VehicleOpActor[SetParameters](vehicle) {

  private case class ParamData(didSet: Parameters, remaining: Map[String, AnyVal], tries: Int = 0) extends ActionRetry with DefaultRetrySettings {
    def action() = if (tries > 0) setParameters(remaining)
    def increment() = copy(tries = tries + 1)
    override def timeoutResult = SetParametersPartialFailure(vehicle, op, didSet, remaining)
  }

  val matcher = SubscribeTo.complete {
    case Packet(From(`id`, _, _), m: ParamValue) if m.paramId != "" => true
  }

  def setParameters(params: Map[String, AnyVal]) =
    params foreach { case (name, value) =>
      val (number, vtype) = valueToParam(value)
      link.send(ParamSet(targetSystem, targetComponent, name, number, vtype))
    }



  // FSM states

  when(Idle) {
    case Event(op: SetParameters, Uninitialized) =>
      start(op, sender())
      link.events.subscribe(self, matcher)
      setParameters(op.params)
      goto(Active) using ParamData(Map.empty, op.params)
  }

  when(Active) {
    case Event(Packet(_, msg: ParamValue), ParamData(didSet, remaining, _)) =>
      val nowSet = didSet + (msg.paramId -> msg.paramValue)
      val nowRemain = remaining - msg.paramId
      if (nowRemain.size > 0) {
        stay using ParamData(nowSet, nowRemain)
      } else {
        stop using Finish(SetParametersResult(vehicle, op, nowSet))
      }
  }
}
