package scavlink.link.mission

import scavlink.link._
import akka.actor.Actor

import scala.concurrent.duration.FiniteDuration

/**
 * Treats the Mission response handler as the only receive handler for an actor.
 */
trait MissionProtocolOnly extends MockMissionProtocol {
  _: Actor with MockVehicle =>
  def receive: Receive = missionHandler
}

/**
 * Simulates Mission requests with no funny business.
 */
class BasicMissionVehicle(val events: LinkEventBus, val initialResultSet: Mission) extends Actor
with MissionProtocolOnly with EventBusResponder with SolidConnection

/**
 * Simulates Mission requests where every Nth packet is duplicated.
 */
class DuplicatingMissionVehicle(val events: LinkEventBus, val initialResultSet: Mission, val every: Int) extends Actor
with MissionProtocolOnly with EventBusResponder with PacketDuplication

/**
 * Simulates Mission requests where every Nth packet is dropped.
 */
class DroppingMissionVehicle(val events: LinkEventBus, val initialResultSet: Mission, val every: Int) extends Actor
with MissionProtocolOnly with EventBusResponder with PacketDrops

/**
 * Simulates Mission requests where every Nth packet is delayed.
 */
class DelayingMissionVehicle(val events: LinkEventBus, val initialResultSet: Mission, val every: Int, val delay: FiniteDuration) extends Actor
with MissionProtocolOnly with EventBusResponder with PacketDelays

/**
 * Simulates Mission requests where every Nth packet is delayed.
 */
class DyingMissionVehicle(val events: LinkEventBus, val initialResultSet: Mission, val after: Int) extends Actor
with MissionProtocolOnly with EventBusResponder with DyingConnection
