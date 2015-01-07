package scavlink.connection

import akka.actor.{Actor, Props}
import scavlink.VehicleInitializer
import scavlink.link.{Link, Vehicle, VehicleInfo}

object VehicleSupervisor {
  def props(events: ConnectionEventBus, link: Link, vehicleInfo: VehicleInfo, initializers: Seq[VehicleInitializer]): Props =
    Props(classOf[VehicleSupervisor], events, link, vehicleInfo, initializers)
}

/**
 * Provides a parent context for starting any number of vehicle-related actors.
 * They will all shut down automatically when the supervisor does.
 */
class VehicleSupervisor(events: ConnectionEventBus, link: Link, vehicleInfo: VehicleInfo, initializers: Seq[VehicleInitializer])
  extends Actor {

  val vehicle = new Vehicle(vehicleInfo, link, initializers)

  override def preStart() = events.publish(VehicleUp(vehicle))
  override def postStop() = events.publish(VehicleDown(vehicle))
  def receive: Receive = Actor.emptyBehavior
}
