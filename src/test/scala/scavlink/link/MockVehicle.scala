package scavlink.link

import akka.actor.Actor.Receive
import scavlink.message.Packet

/**
 * Basic functionality for responding as a vehicle.
 * Various traits may be assembled to produce mock vehicles with different simulated behaviors.
 */
trait MockVehicle {
  val address = "mock"

  protected def sendFn: Packet => Unit

  /**
   * Implements possibly good or bad forms of send behavior. (See trait implementations.)
   */
  protected def send(packet: Packet)

  def receive: Receive
}

/**
 * Simulated packets "sent" from the vehicle are published directly on the event bus as received packets,
 * where they can be picked up by the actors being tested.
 */
trait EventBusResponder extends MockVehicle {
  protected def events: LinkEventBus
  protected val sendFn: Packet => Unit = packet => events.publish(packet)
}

/**
 * Simulates a clean connection: packets are published once, immediately.
 */
trait SolidConnection {
  _: MockVehicle =>

  protected def send(packet: Packet) = sendFn(packet)
}
