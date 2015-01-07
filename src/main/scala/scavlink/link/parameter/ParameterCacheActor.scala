package scavlink.link.parameter

import akka.actor.Status.Failure
import akka.actor._
import scavlink.link.operation._
import scavlink.link._
import scavlink.message.Packet
import scavlink.message.common.ParamValue

import scala.concurrent.duration._

/**
 * Event published whenenver parameters are updated in the cache.
 */
case class ReceivedParameters(vehicle: Vehicle, params: Parameters) extends LinkEvent

case object ClearCache


object ParameterCacheActor {
  def props(vehicle: Vehicle) = Props(classOf[ParameterCacheActor], vehicle)
}

/**
 * Wraps a cache around parameter operations.
 *
 * GetAllParameters returns a cache entry if present, otherwise retrieves the list and updates the cache.
 * GetNamedParameters returns items from the cache if present, otherwise retrieves values and updates the cache.
 * SetParameters updates the cache upon success.
 *
 * The cache implements OpSupervisor, so operations that would result in a cache miss cannot be called simultaneously.
 * However, for any request that is a cache hit, no operation is started, in which case there's no restriction.
 *
 * Also monitors ParamValue messages in case other GCS apps on the network modify parameter values.
 * These messages are ignored if the local library has an operation pending, but if there are no operations,
 * we assume the messages were triggered by another GCS, and use them to update our cache.
 *
 * Since there's no marked end to a stream of ParamValue messages, we must buffer parameters
 * as we receive them and schedule a timeout that fires once they stop arriving after some interval.
 * In the timeout, we check the buffered values against our cache, and if any are different, we
 * update the cache and publish an event.
 *
 * @author Nick Rossi
 */
class ParameterCacheActor(vehicle: Vehicle) extends VehicleOpSupervisor[ParameterOp](vehicle) {
  val opClass = classOf[ParameterOp]

  val link = vehicle.link
  val id = vehicle.id
  val target = vehicle.info.systemId

  // the primary parameter cache
  private var parameterCache: Parameters = Map.empty
  private var hasAll = false

  // for buffering received ParamValue messages, in case they came from another GCS
  private val receiveTimeout = 3.seconds
  private var paramValueBuffer: Parameters = Map.empty


  override def preStart() = {
    link.events.subscribe(self, SubscribeTo.messagesFrom(target, classOf[ParamValue]))
    if (vehicle.settings.autoloadParameters) self ! GetAllParameters()
  }

  override def postStop() = link.events.unsubscribe(self)

  override def opHandler: Receive = {
    case ClearCache =>
      parameterCache = Map.empty
      hasAll = false

    case op: GetAllParameters =>
      if (hasAll) {
        sender() ! GetAllParametersResult(vehicle, op, parameterCache)
      } else {
        submit(op)
      }

    case op@GetNamedParameters(names) =>
      val result = parameterCache.filterKeys(names)
      if (hasAll || result.size == names.size) {
        sender() ! GetNamedParametersResult(vehicle, op, result)
      } else {
        submit(op)
      }

    case op: SetParameters =>
      submit(op)

    case op: Op =>
      sender ! Failure(new IllegalOperationException(op))

    case result: ParameterOpResult =>
      if (result.isAll) {
        parameterCache = result.params
        hasAll = true
      } else {
        parameterCache ++= result.params
      }

      link.events.publish(ReceivedParameters(vehicle, result.params))
      finish(result)


    // monitor ParamValue messages

    case Packet(_, msg: ParamValue) if !opsPending =>
      paramValueBuffer += msg.paramId -> msg.paramValue
      context.setReceiveTimeout(receiveTimeout)

    case ReceiveTimeout =>
      context.setReceiveTimeout(Duration.Undefined)
      val updatedParams = paramValueBuffer.toSet.diff(parameterCache.toSet).toMap
      paramValueBuffer = Map.empty

      if (updatedParams.nonEmpty) {
        parameterCache ++= updatedParams
        link.events.publish(ReceivedParameters(vehicle, updatedParams))
      }
  }
}
