package scavlink.link.operation

import com.typesafe.config.Config
import scavlink.EventMatcher
import scavlink.link.{LinkEvent, SubscribeTo, Vehicle}
import scavlink.message._
import scavlink.settings.SettingsCompanion

import scala.concurrent.duration.FiniteDuration

sealed trait ConversationResult {
  def op: Conversation
}

case class ConversationSucceeded(vehicle: Vehicle, op: Conversation) extends ConversationResult with OpResult
case class ConversationFailed(vehicle: Vehicle, op: Conversation, failedStep: ConversationStep,
                              request: Message, reply: Message) extends OpException with ConversationResult {
  override def toString = s"ConversationFailed(request=$request reply=$reply)"
}

/**
 * Executes a sequential request/reply conversation with the vehicle.
 *
 * Each step of a conversation sends a request message, then matches on a reply message indicating success or failure.
 * If no success or failure message is recevied within a timeout period, the request message is sent again.
 * When one step succeeds, the conversation proceeds to the next step.
 * If a step fails, the conversation is aborted.
 * @author Nick Rossi
 */
class ConversationActor(vehicle: Vehicle) extends VehicleOpActor[Conversation](vehicle) {
  val settings = ConversationSettings(link.config.root)

  case class ConversationData(steps: Seq[ConversationStep], message: Message, timeout: FiniteDuration, tries: Int = 0) extends MessageRetry {
    def increment() = copy(tries = tries + 1)
    val retries: Int = settings.requestRetries
  }

  /**
   * Request next message and subscribe to reply messages.
   */
  def request(steps: Seq[ConversationStep]): ConversationData = {
    val step = steps.head
    val nextMessage = targetMessage(step.request)
    val timeout = step.requestTimeout.getOrElse(settings.requestTimeout)

    link.events.unsubscribe(self)
    link.events.subscribe(self, subscription(step.expectReply))
    ConversationData(steps, nextMessage, timeout)
  }

  /**
   * Set the target system of the outgoing message to the vehicle we're talking to.
   */
  def targetMessage(msg: Message): Message = msg match {
    case m: TargetComponent[_] if m.targetComponent == ComponentId.zero => m.setTarget(targetSystem, targetComponent)
    case m: TargetSystem[_] => m.setTargetSystem(targetSystem)
    case _ => msg
  }

  /**
   * Subscribe to only those messages defined in the expectReply partial function.
   */
  def subscription(expectReply: ExpectMessage): EventMatcher[LinkEvent] = SubscribeTo.complete {
    case Packet(From(`id`, _, _), msg) if expectReply.isDefinedAt(msg) => true
  }


  // FSM states

  when(Idle) {
    case Event(op: Conversation, Uninitialized) =>
      start(op, sender())
      goto(Active) using request(op.steps)
  }

  when(Active) {
    case Event(Packet(_, msg), data: ConversationData) =>
      val steps = data.steps
      if (steps.head.expectReply(msg)) {
        steps.tail match {
          case Nil => stop using Finish(ConversationSucceeded(vehicle, op))
          case moreSteps => stay using request(moreSteps)
        }
      } else {
        stop using Finish(ConversationFailed(vehicle, op, steps.head, data.message, msg))
      }
  }
}


case class ConversationSettings(requestTimeout: FiniteDuration, requestRetries: Int)

object ConversationSettings extends SettingsCompanion[ConversationSettings]("conversation") {
  def fromSubConfig(config: Config): ConversationSettings =
    ConversationSettings(
      getDuration(config, "request-timeout"),
      config.getInt("request-retries")
    )
}
