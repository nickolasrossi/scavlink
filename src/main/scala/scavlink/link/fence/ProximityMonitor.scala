package scavlink.link.fence

import scavlink.coord.Geo
import scavlink.link.nav.SetVehicleModeToHeartbeat
import scavlink.link.operation.Op
import scavlink.link.telemetry.Telemetry
import scavlink.link.{LinkEvent, Vehicle}
import scavlink.message.{Mode, VehicleId}
import scavlink.state.{LocationState, State, SystemState}

case class ProximityWarning(locations: Map[VehicleId, Geo]) extends LinkEvent

/**
 * EXPERIMENTAL:
 * Rudimentary traffic control that flips vehicles to Loiter mode
 * when they move closer than the minimum distance to each other.
 *
 * Note: This is only suitable for testing purposes and to demonstrate how to
 * work with the TrafficControlActor. For serious flying with many vehicles,
 * a robust traffic control algorithm is more appropriate.
 *
 * Note: NOT for use with fixed-wing aircraft; only copters and rovers.
 *
 * @param minimumDistance in meters
 *
 * @author Nick Rossi
 */
class ProximityMonitor(val minimumDistance: Double,
                       val aboveHeight: Double,
                       locations: Map[Vehicle, Geo],
                       modes: Map[Vehicle, Mode],
                       check: Option[Vehicle] = None)
  extends TrafficControl {

  val subscribeTo: Set[Class[_ <: State]] = Set(classOf[LocationState], classOf[SystemState])

  /**
   * Proximity check that compares against a minimum distance,
   * but only if both locations are above the minimum height.
   */
  def isNear(loc1: Geo, loc2: Geo): Boolean =
    loc1.alt >= aboveHeight && loc2.alt >= aboveHeight && loc1.equirectangularDistance(loc2) < minimumDistance

  /**
   * Emergency operation that will be executed in response to a proximity violation.
   * Currently just sets Loiter mode to make the vehicle stop.
   */
  def emergencyOp(vehicle: Vehicle): Op = SetVehicleModeToHeartbeat(vehicle, Mode.Loiter)

  def vehicleUp(vehicle: Vehicle): TrafficControl = this

  def vehicleDown(vehicle: Vehicle): TrafficControl =
    new ProximityMonitor(minimumDistance, aboveHeight, locations - vehicle, modes - vehicle)

  def update(t: Telemetry): TrafficControl = t match {
    case Telemetry(vehicle, state: LocationState, _) =>
      new ProximityMonitor(minimumDistance, aboveHeight, locations + (vehicle -> state.location), modes, Some(vehicle))

    case Telemetry(vehicle, state: SystemState, _) =>
      val mode = Mode.from(vehicle.info.vehicleType, state.specificMode)
      val newModes = mode.map(m => modes + (vehicle -> m))
      new ProximityMonitor(minimumDistance, aboveHeight, locations, newModes.getOrElse(modes), Some(vehicle))

    case _ => this
  }

  /**
   * If the "check" property specifies a vehicle to check, and if it's not already in Loiter mode,
   * check its distance to all other vehicles and return Loiter mode command for any vehicle
   * within minimumDistance and not already in Loiter mode.
   */
  lazy val tripped: Map[Vehicle, Op] = {
    val ops: Option[Map[Vehicle, Op]] = for {
      vehicle <- check
      location <- locations.get(vehicle)
    } yield {
      val vehicles = locations.collect {
        case (ov, ol) if ov == vehicle || isNear(ol, location) => ov
      }

      // if there were no proximity matches, trip nothing
      if (vehicles.size < 2) {
        Map.empty
      } else {
        val nonLoitering = vehicles.filter(v => modes.get(v) != Some(Mode.Loiter))
        nonLoitering.map(v => v -> emergencyOp(v)).toMap
      }
    }

    ops.getOrElse(Map.empty)
  }

  lazy val events: Map[Vehicle, LinkEvent] = {
    val report = locations.filterKeys(tripped.keySet) map { case (vehicle, loc) => vehicle.id -> loc }
    val event = ProximityWarning(report)
    tripped mapValues { _ => event }
  }

  override def toString = s"ProximityWatch(${ minimumDistance }m locations=$locations)"
}


object ProximityMonitor {
  def apply(minimumDistance: Double, aboveHeight: Double): ProximityMonitor =
    new ProximityMonitor(minimumDistance, aboveHeight, Map.empty, Map.empty)
}