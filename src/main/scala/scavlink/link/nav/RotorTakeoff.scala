package scavlink.link.nav

import akka.actor.Status.Failure
import scavlink.link.Vehicle
import scavlink.link.channel.ChannelTellAPI._
import scavlink.link.fence.{FenceActor, FenceBreach}
import scavlink.link.nav.NavTellAPI._
import scavlink.link.operation._
import scavlink.link.telemetry.Telemetry
import scavlink.message.Mode
import scavlink.state.{ChannelState, LocationState}

import scala.concurrent.duration._

/**
 * @param targetHeight climb to height in meters
 * @param initThrottle initial throttle value
 * @param targetThrottle throttle target value
 * @param throttleRampRate ramp throttle %/sec
 */
case class RotorTakeoff(targetHeight: Double, initThrottle: Double, targetThrottle: Double, throttleRampRate: Double)
  extends NavOp {
  require(targetHeight > 0, "Target height must be > 0")
  require(initThrottle >= 0 && initThrottle <= 50, "Initial throttle must be <= 50")
  require(targetThrottle > 50 && targetThrottle <= 100, "Target throttle must be between 50 and 100")
  require(throttleRampRate > 0, "Ramp rate must be > 0")

  val actorType = classOf[RotorTakeoffActor]
}

object RotorTakeoff {
  val Gentle = RotorTakeoff(2, 40, 56, 2)
  val Hard = RotorTakeoff(2, 50, 70, 4)
}

case class RotorTakeoffResult(vehicle: Vehicle, op: RotorTakeoff, finalHeight: Double) extends OpResult
case class RotorTakeoffFailed(vehicle: Vehicle, op: RotorTakeoff, error: RotorTakeoffError.Value, message: String) extends OpException

object RotorTakeoffError extends Enumeration {
  val BadVehicleType, ArmFailure, SetLoiterFailure, FenceBreach = Value
}

/**
 * Executes a controlled takeoff of a rotor vehicle:
 * (1) ramp up the throttle at a specified rate to the 50% level
 * (2) wait 3 seconds for motors to spin up
 * (3) ramp up the throttle to the specified target level
 * (4) maintain the throttle at the target value until the vehicle reaches a target altitude, then
 * (5) reduce the throttle to 50 to maintain altitude.
 *
 * With conservative parameters, this gives the motors time to spin up in unison
 * before the vehicle leaves the ground, followed by a moderated ascent.
 * See RotorTakeoff.Gentle.
 *
 * If vehicle is already at or above the target height, throttle is not adjusted
 * and the operation returns success.
 * @author Nick Rossi
 */
class RotorTakeoffActor(vehicle: Vehicle) extends VehicleOpActor[RotorTakeoff](vehicle) {

  import context.dispatcher

  case class ThrottleValue(throttle: Double) extends OpData
  case class SetThrottle(setPoint: Double)

  case object SetLoiter extends OpState
  case object FirstLocation extends OpState
  case object Arm extends OpState
  case object StartThrottle extends OpState
  case object RampThrottle extends OpState


  // FSM states

  when(Idle) {
    case Event(op: RotorTakeoff, Uninitialized) =>
      start(op, sender())
      if (!vehicle.info.typeInfo.isRotor) {
        stop using Finish(RotorTakeoffFailed(vehicle, op, RotorTakeoffError.BadVehicleType, "Only for rotor vehicles"))
      }

      vehicle.setModeToHeartbeat(Mode.Loiter)
      goto(SetLoiter)
  }

  when(SetLoiter) {
    case Event(_: ConversationSucceeded, _) =>
      link.events.subscribe(self, Telemetry.subscribeTo(vehicle.id, Set(classOf[ChannelState], classOf[LocationState])))
      goto(FirstLocation)

    case Event(Failure(_: ConversationFailed), _) =>
      stop using Finish(RotorTakeoffFailed(vehicle, op, RotorTakeoffError.SetLoiterFailure, "Unable to set Loiter mode"))
  }

  // if already at or above target height, end successfully before doing anything
  when(FirstLocation) {
    case Event(Telemetry(_, state: LocationState, _), _) =>
      if (state.location.alt >= op.targetHeight) {
        stop using Finish(RotorTakeoffResult(vehicle, op, state.location.alt))
      } else {
        vehicle.armMotors(true)
        goto(Arm)
      }
  }

  when(Arm) {
    case Event(_: ConversationSucceeded, _) =>
      goto(StartThrottle)

    case Event(Failure(f: ConversationFailed), _) =>
      stop using Finish(RotorTakeoffFailed(vehicle, op, RotorTakeoffError.ArmFailure, "Unable to arm"))
  }

  when(StartThrottle) {
    case Event(Telemetry(_, state: ChannelState, _), _) =>
      self ! SetThrottle(math.max(state.throttle, op.initThrottle))
      link.events.subscribe(self, FenceActor.subscribeToBreach(vehicle))
      goto(RampThrottle)
  }

  when(RampThrottle) {
    case Event(SetThrottle(t), _) =>
      log.debug(s"Setting throttle to $t")
      vehicle.setThrottle(t)
      val inc = op.throttleRampRate * .25
      val nt = math.min(t + inc, op.targetThrottle)

      // if we're crossing 50, hold at 50 for 3 seconds
      val newThrottle = if (t < 50 && nt >= 50) 50 else nt
      if (newThrottle != t) {
        val delay = if (t == 50) 3.seconds else 250.milliseconds
        context.system.scheduler.scheduleOnce(delay, self, SetThrottle(newThrottle))
      }

      stay using ThrottleValue(newThrottle)

    case Event(Telemetry(_, state: LocationState, _), _) if state.location.alt >= op.targetHeight =>
      vehicle.setThrottle(50)
      stop using Finish(RotorTakeoffResult(vehicle, op, state.location.alt))

    case Event(breach: FenceBreach, _) =>
      stop using Finish(RotorTakeoffFailed(vehicle, op, RotorTakeoffError.FenceBreach,
        s"Fence breach: ${ breach.fences.mkString(",") }"))

    case _ => stay()
  }

  /**
   * If actor is stopping unexpectedly and we were in the middle of a course,
   * set the vehicle to Loiter mode.
   */
  override def unexpectedStop(state: OpState, data: OpData): Unit = (state, data) match {
    case (RampThrottle, ThrottleValue(t)) => if (t > 50) vehicle.setThrottle(50)
    case _ => //
  }
}
