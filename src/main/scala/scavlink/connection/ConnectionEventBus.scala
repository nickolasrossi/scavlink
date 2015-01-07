package scavlink.connection

import scavlink.link.{Link, Vehicle}
import scavlink.message.VehicleId
import scavlink.{BaseEventBus, SubscribeToEvents}

sealed trait ConnectionEvent
case class LinkUp(link: Link) extends ConnectionEvent
case class LinkDown(link: Link) extends ConnectionEvent
case class VehicleUp(vehicle: Vehicle) extends ConnectionEvent
case class VehicleDown(vehicle: Vehicle) extends ConnectionEvent
case class Vehicles(vehicles: Map[VehicleId, Vehicle]) extends ConnectionEvent

/**
 * Distributes connection-related events:
 * - when a communication link goes up or down
 * - when a vehicle on a link goes up or down
 *
 * Application code can subscribe to receive VehicleUp messages, obtain the vehicle reference,
 * and perform subsequent actions.
 */
class ConnectionEventBus extends BaseEventBus[ConnectionEvent]

object ConnectionSubscribeTo extends SubscribeToEvents[ConnectionEvent]
