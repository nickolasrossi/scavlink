package scavlink.link

import scavlink.link.operation.{Op, OpSupervisor}

/**
 * A supervisor for vehicle operations.
 */
abstract class VehicleOpSupervisor[O <: Op](val vehicle: Vehicle) extends OpSupervisor[O, Vehicle] {
  def opProps: Vehicle = vehicle
}