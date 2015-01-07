package scavlink.link.command

import scavlink.link.command.CommandTestData._
import scavlink.link.{MockVehicle, SentPacket}
import scavlink.message.common.{CommandAck, CommandLong}
import scavlink.message.{From, VehicleId, Packet}
import akka.actor.Actor.Receive
import akka.actor.ActorContext

trait MockCommandProtocol {
  self: MockVehicle =>

  def responses: CommandResponses
  implicit def context: ActorContext

  def commandHandler: Receive = {
    case SentPacket(Packet(from, cmd: CommandLong)) =>
      val (delay, response) = responses(cmd.command)

      // respond to all CommandLong requests with a CommandAck
      context.system.scheduler.scheduleOnce(delay)({
        send(Packet(From(VehicleId.fromLink(address, cmd.targetSystem), cmd.targetSystem, cmd.targetComponent), CommandAck(cmd.command, response.id.toByte)))
      })(context.dispatcher)
  }
}
