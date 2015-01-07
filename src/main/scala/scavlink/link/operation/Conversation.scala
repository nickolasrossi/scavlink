package scavlink.link.operation

import scavlink.message.common.CommandAck
import scavlink.message.enums.MavResult
import scavlink.message.{Command, Message}

import scala.concurrent.duration.FiniteDuration

/**
 * Represents a sequential request/reply conversation with a vehicle.
 * Can be subclassed to tag a conversation class with a specific Op subtype
 * for use as a primary operation.
 * @see [[scavlink.link.nav.NavOp]]
 * @author Nick Rossi
 */
class Conversation(val steps: Seq[ConversationStep]) extends Op {
  def this(step: ConversationStep) = this(Seq(step))
  
  require(steps.nonEmpty)
  val actorType = classOf[ConversationActor]
  override def toString = s"Conversation($steps)"
}

/**
 * Creates a conversation without any inheritance.
 */
object Conversation {
  def apply(steps: Seq[ConversationStep]): Conversation = new Conversation(steps)
  def apply(step: ConversationStep): Conversation = apply(Seq(step))
  def apply(command: Command): Conversation = apply(ConversationStep(command))
  def commands(commands: Command*): Conversation = apply(commands.map(ConversationStep.apply))
}


/**
 * Represents a single step in a conversation with a vehicle.
 * @param request the message sent for this step
 * @param expectReply partial function that matches expected reply messages that represent success or failure
 * @param requestTimeout optional override of configured request message timeout
 *
 * @author Nick Rossi
 */
case class ConversationStep(request: Message, requestTimeout: Option[FiniteDuration] = None)(val expectReply: ExpectMessage)

object ConversationStep {
  /**
   * Conversation step that turns any Command into a CommandLong message and expects a CommandAck reply.
   */
  def apply(command: Command): ConversationStep = {
    val cmd = command.cmd
    ConversationStep(command.message) {
      case CommandAck(`cmd`, result) => result == MavResult.ACCEPTED.id
    }
  }
}
