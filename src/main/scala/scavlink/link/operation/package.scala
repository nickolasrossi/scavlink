package scavlink.link

import scavlink.message.Message

/**
 * This package implements "operations", which are orchestrations of MAVLink requests
 * and replies to achieve a result.
 *
 * An operation is implemented by a dedicated actor that orchestrates messages and replies,
 * along with actor messages that extend [[scavlink.link.operation.Op]] and
 * [[scavlink.link.operation.OpResult]]. The design allows developers to implement
 * their own operations.
 *
 * [[scavlink.link.operation.VehicleOpActor]] is a convenient base class for implementing operations
 * on a single vehicle. It's based on Akka's Finite State Machine and provides a simple progression
 * of states with timeouts.
 *
 * [[scavlink.link.operation.OpSupervisor]] is the entry point for invoking operations of a
 * particular category. See the docs there for more info.
 *
 * [[scavlink.link.operation.Conversation]] represents a sequential request/reply conversation with a vehicle.
 * It's not appropriate for complex orchestrations, but sometimes you just need to send a message and wait for
 * a particular reply (and sometimes, several in sequence).
 *
 * @author Nick Rossi
 */
package object operation {
  /**
   * In a [[Conversation]], returns whether a received message represents success or failure.
   */
  type ExpectMessage = PartialFunction[Message, Boolean]
}
