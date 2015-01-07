package scavlink.link.command

import scavlink.link._
import scavlink.link.command.CommandTestData.CommandResponses
import akka.actor.Actor

import scala.concurrent.duration.FiniteDuration

/**
 * Treats the Command response handler as the only receive handler for an actor.
 */
trait CommandProtocolOnly extends MockCommandProtocol {
  _: Actor with MockVehicle =>
  def receive: Receive = commandHandler
}

/**
 * Simulates Command requests with no funny business.
 */
class BasicCommandVehicle(val events: LinkEventBus, val responses: CommandResponses) extends Actor
with CommandProtocolOnly with EventBusResponder with SolidConnection

/**
 * Simulates Command requests where every Nth packet is duplicated.
 */
class DuplicatingCommandVehicle(val events: LinkEventBus, val responses: CommandResponses, val every: Int) extends Actor
with CommandProtocolOnly with EventBusResponder with PacketDuplication

/**
 * Simulates Command requests where every Nth packet is dropped.
 */
class DroppingCommandVehicle(val events: LinkEventBus, val responses: CommandResponses, val every: Int) extends Actor
with CommandProtocolOnly with EventBusResponder with PacketDrops

/**
 * Simulates Command requests where every Nth packet is delayed.
 */
class DelayingCommandVehicle(val events: LinkEventBus, val responses: CommandResponses, val every: Int, val delay: FiniteDuration) extends Actor
with CommandProtocolOnly with EventBusResponder with PacketDelays

/**
 * Simulates Command requests where every Nth packet is delayed.
 */
class DyingCommandVehicle(val events: LinkEventBus, val responses: CommandResponses, val after: Int) extends Actor
with CommandProtocolOnly with EventBusResponder with DyingConnection
