package scavlink.link.parameter

import scavlink.link._
import scavlink.link.parameter.types.ParameterResultSet
import akka.actor.Actor

import scala.concurrent.duration.FiniteDuration

/**
 * Treats the parameter response handler as the only receive handler for an actor.
 */
trait ParameterProtocolOnly extends MockParameterProtocol {
  _: Actor with MockVehicle =>
  def receive: Receive = parameterHandler
}

/**
 * Simulates parameter requests with no funny business.
 */
class BasicParameterVehicle(val events: LinkEventBus, val initialResultSet: ParameterResultSet) extends Actor
with ParameterProtocolOnly with EventBusResponder with SolidConnection

/**
 * Simulates parameter requests where every Nth packet is duplicated.
 */
class DuplicatingParameterVehicle(val events: LinkEventBus, val initialResultSet: ParameterResultSet, val every: Int) extends Actor
with ParameterProtocolOnly with EventBusResponder with PacketDuplication

/**
 * Simulates parameter requests where every Nth packet is dropped.
 */
class DroppingParameterVehicle(val events: LinkEventBus, val initialResultSet: ParameterResultSet, val every: Int) extends Actor
with ParameterProtocolOnly with EventBusResponder with PacketDrops

/**
 * Simulates parameter requests where every Nth packet is delayed.
 */
class DelayingParameterVehicle(val events: LinkEventBus, val initialResultSet: ParameterResultSet, val every: Int, val delay: FiniteDuration) extends Actor
with ParameterProtocolOnly with EventBusResponder with PacketDelays

/**
 * Simulates parameter requests where every Nth packet is delayed.
 */
class DyingParameterVehicle(val events: LinkEventBus, val initialResultSet: ParameterResultSet, val after: Int) extends Actor
with ParameterProtocolOnly with EventBusResponder with DyingConnection
