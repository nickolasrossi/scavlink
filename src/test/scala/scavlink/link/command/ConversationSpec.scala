package scavlink.link.command

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.pattern.ask
import scavlink.link.command.CommandTestData.CommandResponses
import scavlink.link.operation._
import scavlink.message.common._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class ConversationSpec(_system: ActorSystem) extends VehicleOpSpecSupport(_system) with CommandTestData {

  def this() = this(ActorSystem("ConversationSpec"))

  def withBasicResponder(responses: CommandResponses) =
    withResponder(Props(classOf[BasicCommandVehicle], events, responses)) _

  def withDroppingResponder(responses: CommandResponses, every: Int) =
    withResponder(Props(classOf[DroppingCommandVehicle], events, responses, every)) _

  def withCommandActor(testCode: ActorRef => Any): Unit =
    withActor(classOf[ConversationActor])(testCode)

  def holdConversation(actor: ActorRef, conversation: Conversation, duration: Duration = 15.seconds) = {
    Given("sending a Conversation message to the actor")
    val future1 = (actor ? conversation).mapTo[ConversationSucceeded]
    Await.ready(future1, duration)
    future1.value.get
  }

  def expectSuccess(actor: ActorRef, conversation: Conversation, duration: Duration = 15.seconds) = {
    val result = holdConversation(actor, conversation, duration)

    Then("the result returns success")
    result shouldBe Success(ConversationSucceeded(vehicle, conversation))
  }

  def expectFailure(actor: ActorRef, conversation: Conversation, duration: Duration = 15.seconds) = {
    val result = holdConversation(actor, conversation)

    Then("the result returns failure")
    result match {
      case Failure(_: ConversationFailed) => // success
      case r => fail(s"expected: ConversationFailed; actual: $r")
    }
  }

  
  "the Conversation actor" should {
    "return success for a single step" in
      withBasicResponder(navResponses) {
        withCommandActor { actor =>
          expectSuccess(actor, Conversation(NavWaypoint()))
        }
      }

    "return success for a list of successful commands" in
      withBasicResponder(navResponses) {
        withCommandActor { actor =>
          val conversation = Conversation.commands(
            ComponentArmDisarm(),
            NavWaypoint(),
            NavWaypoint(),
            NavReturnToLaunch()
          )

          expectSuccess(actor, conversation)
        }
      }

    "return a failure with the index of the command that failed" in
      withBasicResponder(navResponses) {
        withCommandActor { actor =>
          val conversation = Conversation.commands(
            ComponentArmDisarm(),
            NavWaypoint(),
            DoJump(),
            NavReturnToLaunch()
          )

          expectFailure(actor, conversation)
        }
      }

    "return a failure that takes 10 seconds to respond" in
      withBasicResponder(navResponses) {
        withCommandActor { actor =>
          val conversation = Conversation.commands(
            ComponentArmDisarm(),
            NavWaypoint(),
            DoParachute(),
            NavReturnToLaunch()
          )

          expectFailure(actor, conversation, 20.seconds)
        }
      }
    
    "succeed even when packets are dropped" in 
      withDroppingResponder(navResponses, 3) {
        withCommandActor { actor =>
          expectSuccess(actor, Conversation.commands(
            ComponentArmDisarm(),
            NavWaypoint(),
            NavReturnToLaunch()
          ), 20.seconds)
        }
      }
  }
}