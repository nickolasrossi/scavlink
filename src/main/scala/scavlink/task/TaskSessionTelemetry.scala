package scavlink.task

import akka.actor.{Cancellable, Actor, ActorLogging}
import scavlink.EventMatcher
import scavlink.link.{LinkEvent, Vehicle}
import scavlink.message.{Mode, VehicleId}
import scavlink.state._
import scavlink.util.Memoize
import scavlink.link.telemetry.{Telemetry => LinkTelemetry}
import scavlink.task.Protocol._


import scala.collection.mutable
import scala.concurrent.duration._

/**
 * Handling for requests and delivery of vehicle telemetry on a WebSocket session.
 * @author Nick Rossi
 */
trait TaskSessionTelemetry {
  _: Actor with ActorLogging =>

  import context.dispatcher

  private var telemetry: Map[VehicleId, Telemetry] = Map.empty
  private var sendCycles: mutable.Map[VehicleId, Cancellable] = mutable.Map.empty

  // need to memoize matchers so that they can be unsubscribed
  private val matchers = Memoize(telemetrySubscription)

  private def telemetrySubscription(id: VehicleId): EventMatcher[LinkEvent] =
    scavlink.link.telemetry.Telemetry.subscribeTo(id, Set(
      classOf[LocationState],
      classOf[SystemState],
      classOf[MotionState],
      classOf[ChannelState],
      classOf[BatteryState],
      classOf[GpsState]
    ))

  def send(output: String): Unit


  // actor receive handler

  def receiveTelemetry: Receive = {
    case LinkTelemetry(vehicle, state, _) =>
      val telem = telemetry.getOrElse(vehicle.id, Telemetry(vehicle.id))
      telemetry += vehicle.id -> updateTelemetry(vehicle, telem, state)
  }


  /**
   * Update combined telemetry for a vehicle with the new state.
   */
  def updateTelemetry(vehicle: Vehicle, telem: Telemetry, state: State): Telemetry = state match {
    case s: LocationState =>
      telem.copy(
        location = s.location,
        heading = s.heading
      )

    case s: MotionState =>
      telem.copy(
        course = s.courseOverGround,
        groundspeed = s.groundspeed,
        climb = s.climb
      )

    case s: SystemState =>
      telem.copy(
        state = s.systemState,
        mode = Mode.from(vehicle.info.vehicleType, s.specificMode).getOrElse(Mode.Unknown)
      )

    case s: ChannelState =>
      telem.copy(throttle = s.throttle)

    case s: GpsState =>
      telem.copy(gpsFix = s.fixType)

    case s: BatteryState =>
      telem.copy(batteryVoltage = s.voltage)
  }


  // protocol requests

  def startTelemetry(vehicle: Vehicle, interval: Int): Unit = {
    val matcher = matchers(vehicle.id)
    vehicle.link.events.subscribe(self, matcher)

    val cycle = context.system.scheduler.schedule(interval.seconds, interval.seconds) {
      telemetry.get(vehicle.id) foreach { telem =>
        send(writeResponse(telem))
      }
    }
    sendCycles += vehicle.id -> cycle
  }

  def stopTelemetry(vehicle: Vehicle): Unit = {
    sendCycles.remove(vehicle.id).foreach(_.cancel())
    telemetry -= vehicle.id
    val unsubscribed = vehicle.link.events.unsubscribe(self, matchers(vehicle.id))
    if (!unsubscribed) {
      log.warning(s"Did not unsubscribe from vehicle ${vehicle.id} telemetry messages")
    }
  }
}
