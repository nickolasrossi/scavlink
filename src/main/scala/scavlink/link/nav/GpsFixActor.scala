package scavlink.link.nav

import scavlink.link.Vehicle
import scavlink.link.operation._
import scavlink.link.telemetry.Telemetry
import scavlink.state.{GpsFixType, GpsState}

import scala.concurrent.duration._

case class AwaitGpsFix(minSatellies: Int) extends NavOp {
  val actorType = classOf[GpsFixActor]
}

case class AwaitGpsFixResult(vehicle: Vehicle, op: AwaitGpsFix) extends OpResult
case class AwaitGpsFixFailed(vehicle: Vehicle, op: AwaitGpsFix, error: String) extends OpException

/**
 * Waits for a GPS fix to be established, which is necessary before any navigation can take place.
 */
class GpsFixActor(vehicle: Vehicle) extends VehicleOpActor[AwaitGpsFix](vehicle) {
  import context.dispatcher

  case object Await extends OpState
  case object End extends OpState

  when(Idle) {
    case Event(op: AwaitGpsFix, Uninitialized) =>
      start(op, sender())
      link.events.subscribe(self, Telemetry.subscribeTo(vehicle, Set(classOf[GpsState])))
      goto(Await)
  }

  when(Await, stateTimeout = 1.minute) {
    // when GPS fix changes from None to something, wait a couple seconds before signaling success.
    // if an arming operation is performed too soon after GPS fix is established, a GPS glitch can happen.
    // todo: is this because we need to wait for a valid location as well?
    case Event(Telemetry(_, state: GpsState, oldState: GpsState), _)
      if oldState.fixType <= GpsFixType.None && state.fixType > GpsFixType.None
        && state.satellitesVisible >= op.minSatellies =>
      context.system.scheduler.scheduleOnce(2.seconds, self, End)
      goto(End)

    case Event(Telemetry(_, state: GpsState, _), _)
      if state.fixType > GpsFixType.None && state.satellitesVisible >= op.minSatellies =>
      stop using Finish(AwaitGpsFixResult(vehicle, op))

    case Event(StateTimeout, _) =>
      stop using Finish(AwaitGpsFixFailed(vehicle, op, "No GPS fix detected"))
  }

  when(End) {
    case Event(End, _) =>
      stop using Finish(AwaitGpsFixResult(vehicle, op))
  }
}
