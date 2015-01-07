package scavlink.link.parameter

import scavlink.link._
import scavlink.link.operation._
import scavlink.message.common.{ParamRequestList, ParamRequestRead, ParamValue}
import scavlink.message.{From, Packet, Unsigned}

case class GetAllParameters() extends ParameterOp {
  val actorType = classOf[GetAllParametersActor]
}

case class GetAllParametersResult(vehicle: Vehicle, op: GetAllParameters, params: Parameters) extends ParameterOpResult {
  val isAll = true
}

/**
 * Retrieves the full parameter list from the vehicle.
 * @author Nick Rossi
 */
class GetAllParametersActor(vehicle: Vehicle) extends VehicleOpActor[GetAllParameters](vehicle) {
  type PartialParameters = scala.collection.mutable.Seq[Option[ParamValue]]

  private case class ParamData(params: PartialParameters, numReceived: Int, tries: Int = 0) extends ActionRetry with DefaultRetrySettings {
    // only invoke action if tries > 0, since we don't need to send a new request message for every received parameter
    def action() = if (tries > 0) {
      if (params.indexWhere(_ != None) >= 0) {
        val emptyIndices = params.zipWithIndex.filter { case (pv, i) => pv == None }.map(_._2)
        emptyIndices foreach { i =>
          link.send(ParamRequestRead(targetSystem, targetComponent, "", i.toShort))
        }
      } else {
        link.send(ParamRequestList(targetSystem, targetComponent))
      }
    }

    def increment(): ActionRetry = copy(tries = tries + 1)
  }

  val matcher = SubscribeTo.complete {
    case Packet(From(`id`, _, _), msg: ParamValue) if msg.paramIndex != -1 => true
  }


  // FSM states

  when(Idle) {
    case Event(op: GetAllParameters, Uninitialized) =>
      start(op, sender())
      link.events.subscribe(self, matcher)
      link.send(ParamRequestList(targetSystem, targetComponent))
      goto(Active) using ParamData(scala.collection.mutable.Seq.empty, 0)
  }

  when(Active) {
    case Event(Packet(_, msg: ParamValue), ParamData(params, numReceived, _)) =>
      val count = Unsigned(msg.paramCount)
      val index = Unsigned(msg.paramIndex)
      val ps = if (params.length < count) params.padTo(count, None) else params

      val oldValue = ps(index)
      ps(index) = Some(msg)
      val nowReceived = if (oldValue == None) numReceived + 1 else numReceived

      if (nowReceived < ps.length) {
        stay using ParamData(ps, nowReceived)
      } else {
        val result = ps.collect { case Some(v) => v.paramId -> v.paramValue }.toMap
        stop using Finish(GetAllParametersResult(vehicle, op, result))
      }
  }
}
