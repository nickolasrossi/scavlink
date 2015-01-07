package scavlink.link.operation

import akka.actor.Status.Failure
import akka.actor.{ActorRef, FSM}
import com.typesafe.config.Config
import scavlink.link.Vehicle
import scavlink.message.Message
import scavlink.settings.SettingsCompanion

import scala.concurrent.duration.FiniteDuration

trait OpState
case object Idle extends OpState
case object Active extends OpState

trait OpData
case object Uninitialized extends OpData
case class Finish(result: OpResult) extends OpData

case class OpTimeout(vehicle: Vehicle, op: Op) extends OpException

/**
 * Base behavior for orchestrated operations on a single vehicle.
 * Based on Akka's Finite State Machine.
 * @author Nick Rossi
 */
abstract class VehicleOpActor[O <: Op](vehicle: Vehicle) extends FSM[OpState, OpData] {
  private var _op: Option[O] = None
  private var _origin: Option[ActorRef] = None

  val link = vehicle.link
  val id = vehicle.id
  val vehicleType = vehicle.info.vehicleType
  val targetSystem = vehicle.info.systemId
  val targetComponent = vehicle.info.defaultComponentId

  val operationSettings = OperationSettings(vehicle.link.config.root)

  final def op: O = _op.get
  final def origin: ActorRef = _origin.get

  override def preStart() = initialize()

  override def postStop() = {
    super.postStop()
    link.events.unsubscribe(self)
  }

  def handleTimeout: StateFunction = {
    case Event(StateTimeout, retry: ActionRetry) =>
      if (retry.shouldKeepTrying) {
        stay using retry.increment()
      } else {
        stop using Finish(retry.timeoutResult)
      }

    case x =>
      log.debug(s"Ignoring unhandled event $x")
      stay()
  }

  /**
   * Override to implement cleanup if the actor is stopping unexpectedly.
   */
  def unexpectedStop(state: OpState, data: OpData): Unit = {}


  // default parts of the state machine

  startWith(Idle, Uninitialized)

  protected def start(op: O, origin: ActorRef) = {
    _op = Some(op)
    _origin = Some(origin)
  }

  whenUnhandled(handleTimeout)

  onTransition {
    case oldState -> newState =>
      nextStateData match {
        case retry: ActionRetry => setStateTimeout(newState, Some(retry.timeout))
        case _ => //
      }
  }

  onTransition {
    case _ -> Idle => link.events.unsubscribe(self)
  }

  onTermination {
    case StopEvent(FSM.Failure(cause: OpResult), _, _) => finish(Finish(cause))
    case StopEvent(FSM.Failure(cause: OpData), _, _) => finish(cause)
    case StopEvent(FSM.Shutdown, state, data) => unexpectedStop(state, data); finish(data)
    case StopEvent(_, _, data) => finish(data)
  }

  protected def finish(data: OpData) = {
    link.events.unsubscribe(self)

    if (_origin.isDefined) {
      data match {
        case Finish(result: Exception) => origin ! Failure(result)
        case Finish(result) => origin ! result
        case _ => origin ! Failure(new UnexpectedTerminationException(op))
      }
    }
  }


  // retry logic

  trait ActionRetry extends OpData {
    def action(): Unit
    def timeout: FiniteDuration
    def retries: Int
    def timeoutResult: OpResult = OpTimeout(vehicle, op)

    def tries: Int
    def increment(): ActionRetry
    def shouldKeepTrying: Boolean = tries < retries

    // invoke the action once here on creation, since we don't get a transition event on goto(<same state>)
    // (despite what the Akka docs say)
    action()
  }

  trait DefaultRetrySettings {
    val timeout = operationSettings.messageTimeout
    val retries = operationSettings.messageRetries
  }

  trait MessageRetry extends ActionRetry {
    def message: Message
    def action() = link.send(message)
  }

  trait DefaultMessageRetry extends MessageRetry with DefaultRetrySettings

  case class MessageData(message: Message, tries: Int = 0) extends DefaultMessageRetry {
    def increment() = copy(tries = tries + 1)
  }
}


case class OperationSettings(messageTimeout: FiniteDuration, messageRetries: Int, telemetryTimeout: FiniteDuration)

object OperationSettings extends SettingsCompanion[OperationSettings]("operation") {
  def fromSubConfig(config: Config): OperationSettings =
    OperationSettings(
      getDuration(config, "message-timeout"),
      config.getInt("message-retries"),
      getDuration(config, "telemetry-timeout")
    )
}
