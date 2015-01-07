package scavlink.link.nav

import akka.actor.Props
import scavlink.link.{Vehicle, VehicleOpSupervisor}

object NavOpSupervisor {
  def props(vehicle: Vehicle): Props = Props(classOf[NavOpSupervisor], vehicle)
}

/**
 * Supervisor for vehicle navigation operations.
 */
class NavOpSupervisor(vehicle: Vehicle) extends VehicleOpSupervisor[NavOp](vehicle) {
  val opClass = classOf[NavOp]
}
