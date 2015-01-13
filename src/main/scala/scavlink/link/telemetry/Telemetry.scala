package scavlink.link.telemetry

import scavlink.link.{SubscribeTo, LinkEvent, Vehicle}
import scavlink.message.VehicleId
import scavlink.state.State

/**
 * Telemetry event for a single state change.
 */
case class Telemetry(vehicle: Vehicle, state: State, oldState: State) extends LinkEvent {
  override def toString = s"Telemetry($state at ${ state.timestamp })"
}

object Telemetry {
  /**
   * Subscribe to a set of telemetry states.
   */
  def subscribeTo(id: VehicleId, states: Set[Class[_ <: State]]) = SubscribeTo.complete {
    case Telemetry(v, state, _) if id == v.id && states.contains(state.getClass) => true
  }

  /**
   * Subscribe to all telemetry states.
   */
  def subscribeToAll(vehicle: VehicleId) = SubscribeTo.complete {
    case e: Telemetry if e.vehicle.id == vehicle => true
  }
}
