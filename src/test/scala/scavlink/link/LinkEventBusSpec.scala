package scavlink.link

import akka.actor.ActorSystem
import akka.testkit.{TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, GivenWhenThen, Matchers, WordSpecLike}
import scavlink.EventMatcher
import scavlink.connection.frame.LostPackets
import scavlink.message.{VehicleId, Packet}
import scavlink.message.common._

import scala.language.reflectiveCalls

class LinkEventBusSpec(_system: ActorSystem) extends TestKit(_system)
with WordSpecLike with Matchers with BeforeAndAfterAll with GivenWhenThen {

  case object TestEvent extends LinkEvent

  def this() = this(ActorSystem("LinkEventBusSpec"))

  val id = VehicleId("spec")
  
  def fixture = new {
    val probe = new TestProbe(system)
    val bus = new LinkEventBus
  }

  "A LinkEventBus" when {
    "subscribed with SubscribeTo.all()" should {
      "pass all received events" in {
        val f = fixture
        val probe = f.probe
        val bus = f.bus

        Given("an actor subscribed to all bus events")
        bus.subscribeToAll(probe.ref)

        When("SentPacket is published")
        bus.publish(SentPacket(Packet(id, 1, 1, SystemTime())))
        Then("the SentPacket event is received")
        probe.expectMsg(SentPacket(Packet(id, 1, 1, SystemTime())))

        When("Packet is published")
        bus.publish(Packet(id, 147, 223, Heartbeat()))
        Then("the Packet event is received")
        probe.expectMsg(Packet(id, 147, 223, Heartbeat()))

        When("two events are published")
        bus.publish(Packet(id, 2, 2, AuthKey()))
        bus.publish(ReceiveError(LostPackets(4)))
        Then("both events are received")
        probe.expectMsg(Packet(id, 2, 2, AuthKey()))
        probe.expectMsg(ReceiveError(LostPackets(4)))
      }
    }

    "called with SubscribeTo.event()" should {
      "pass only the specified event" in {
        val f = fixture
        val probe = f.probe
        val bus = f.bus

        Given("an actor subscribed to one type of event")
        bus.subscribe(probe.ref, SubscribeTo.event(classOf[Packet]))

        When("that event type is published")
        bus.publish(Packet(id, 33, 55, Heartbeat()))
        Then("the event is received")
        probe.expectMsg(Packet(id, 33, 55, Heartbeat()))

        When("some other event type is published")
        bus.publish(TestEvent)
        Then("that event is not received")
        probe.expectNoMsg()
      }
    }

    "subscribed with SubscribeTo.events()" should {
      "pass any of the specified events" in {
        val f = fixture
        val probe = f.probe
        val bus = f.bus

        Given("an actor subscribed to several event types")
        bus.subscribe(probe.ref, SubscribeTo.events(classOf[Packet], classOf[SentPacket]))

        When("one of those events type is published")
        bus.publish(Packet(id, 33, 55, Heartbeat()))
        bus.publish(SentPacket(Packet(id, 88, 222, AuthKey())))
        Then("the event is received")
        probe.expectMsg(Packet(id, 33, 55, Heartbeat()))
        probe.expectMsg(SentPacket(Packet(id, 88, 222, AuthKey())))

        When("some other event type is published")
        bus.publish(ReceiveError(LostPackets(200)))
        Then("that event is not received")
        probe.expectNoMsg()
      }
    }

    "subscribed with SubscribeTo.eventsExcept()" should {
      "pass any event except the specified ones" in {
        val f = fixture
        val probe = f.probe
        val bus = f.bus

        Given("an actor subscribed to all events except certain types")
        bus.subscribe(probe.ref, SubscribeTo.eventsExcept(classOf[SentPacket]))

        When("one of those event types is published")
        bus.publish(SentPacket(Packet(id, 88, 222, AuthKey())))
        Then("that event is not received")
        probe.expectNoMsg()

        When("other events are published")
        bus.publish(Packet(id, 33, 55, Heartbeat()))
        bus.publish(ReceiveError(LostPackets(200)))
        bus.publish(TestEvent)
        Then("those events are received")
        probe.expectMsg(Packet(id, 33, 55, Heartbeat()))
        probe.expectMsg(ReceiveError(LostPackets(200)))
        probe.expectMsg(TestEvent)
      }
    }

    "subscribed with SubscribeTo.allMessages()" should {
      "pass through all received messages" in {
        val f = fixture
        val probe = f.probe
        val bus = f.bus

        Given("an actor subscribed to all messages")
        bus.subscribe(probe.ref, SubscribeTo.allMessages)

        When("any message is published")
        bus.publish(Packet(id, 63, 11, ParamRequestList()))
        bus.publish(Packet(id, 22, 245, RawImu()))
        Then("that event is received")
        probe.expectMsg(Packet(id, 63, 11, ParamRequestList()))
        probe.expectMsg(Packet(id, 22, 245, RawImu()))

        When("any other event is published")
        bus.publish(SentPacket(Packet(id, 33, 55, Heartbeat())))
        bus.publish(ReceiveError(LostPackets(200)))
        bus.publish(TestEvent)
        Then("those events are not received")
        probe.expectNoMsg()
      }
    }

    "subscribed with SubscribeTo.message() with no component" should {
      "pass the specified message for all components" in {
        val f = fixture
        val probe = f.probe
        val bus = f.bus

        Given("an actor subscribed to one message")
        bus.subscribe(probe.ref, SubscribeTo.message(classOf[Heartbeat]))

        When("that message is published")
        bus.publish(Packet(id, 63, 11, Heartbeat()))
        bus.publish(Packet(id, 22, 245, Heartbeat()))
        Then("that event is received")
        probe.expectMsg(Packet(id, 63, 11, Heartbeat()))
        probe.expectMsg(Packet(id, 22, 245, Heartbeat()))

        When("any other message is published")
        bus.publish(Packet(id, 63, 11, ParamRequestList()))
        bus.publish(Packet(id, 22, 245, RawImu()))
        Then("those events are not received")
        probe.expectNoMsg()
      }
    }

    "subscribed with SubscribeTo.messages() with no component" should {
      "pass any of the messages for all components" in {
        val f = fixture
        val probe = f.probe
        val bus = f.bus

        Given("an actor subscribed to multiple messages")
        bus.subscribe(probe.ref, SubscribeTo.messages(classOf[Heartbeat], classOf[SysStatus]))

        When("any of those messages is published")
        bus.publish(Packet(id, 44, 55, Heartbeat()))
        bus.publish(Packet(id, 99, 111, SysStatus()))
        Then("those events are received")
        probe.expectMsg(Packet(id, 44, 55, Heartbeat()))
        probe.expectMsg(Packet(id, 99, 111, SysStatus()))

        When("any other message is published")
        bus.publish(Packet(id, 63, 11, ParamRequestList()))
        bus.publish(Packet(id, 22, 245, RawImu()))
        Then("those events are not received")
        probe.expectNoMsg()
      }
    }

    "subscribed with SubscribeTo.messagesExcept() with no component" should {
      "pass any message except those specified" in {
        val f = fixture
        val probe = f.probe
        val bus = f.bus

        Given("an actor subscribed to messages except those specified")
        bus.subscribe(probe.ref, SubscribeTo.messagesExcept(classOf[Heartbeat], classOf[SysStatus]))

        When("other messages are published")
        bus.publish(Packet(id, 63, 11, ParamRequestList()))
        bus.publish(Packet(id, 22, 245, RawImu()))
        Then("those events are received")
        probe.expectMsg(Packet(id, 63, 11, ParamRequestList()))
        probe.expectMsg(Packet(id, 22, 245, RawImu()))

        When("the specified messages are published")
        bus.publish(Packet(id, 44, 55, Heartbeat()))
        bus.publish(Packet(id, 99, 111, SysStatus()))
        Then("those events are not received")
        probe.expectNoMsg()
      }
    }

    "subscribed with SubscribeTo.message(component,...)" should {
      "pass the message for the specified component only" in {
        val f = fixture
        val probe = f.probe
        val bus = f.bus

        Given("an actor subscribed to one message for a specific component")
        bus.subscribe(probe.ref, SubscribeTo.messageFrom(44, 55, classOf[Heartbeat]))

        When("that message + the component is published")
        bus.publish(Packet(id, 44, 55, Heartbeat()))
        Then("that event is received")
        probe.expectMsg(Packet(id, 44, 55, Heartbeat()))

        When("any other component is published with the same message")
        bus.publish(Packet(id, 63, 11, Heartbeat()))
        bus.publish(Packet(id, 22, 245, Heartbeat()))
        Then("those events are not received")
        probe.expectNoMsg()
      }
    }

    "subscribed with SubscribeTo.messages(component,...)" should {
      "pass any of the messages for the specified component only" in {
        val f = fixture
        val probe = f.probe
        val bus = f.bus

        Given("an actor subscribed to multiple messages for a specific component")
        bus.subscribe(probe.ref, SubscribeTo.messagesFrom(44, 55, classOf[Heartbeat], classOf[SysStatus]))

        When("any of those messages + the component is published")
        bus.publish(Packet(id, 44, 55, Heartbeat()))
        bus.publish(Packet(id, 44, 55, SysStatus()))
        Then("those events are received")
        probe.expectMsg(Packet(id, 44, 55, Heartbeat()))
        probe.expectMsg(Packet(id, 44, 55, SysStatus()))

        When("any other component is published with the same messages")
        bus.publish(Packet(id, 63, 11, Heartbeat()))
        bus.publish(Packet(id, 22, 245, SysStatus()))
        Then("those events are not received")
        probe.expectNoMsg()
      }
    }

    "subscribed with SubscribeTo.messagesExcept(component,...)" should {
      "pass any message except specified, only for the specified component" in {
        val f = fixture
        val probe = f.probe
        val bus = f.bus

        Given("an actor subscribed to messages for a component except those specified")
        bus.subscribe(probe.ref, SubscribeTo.messagesExcept(44, 55, classOf[Heartbeat], classOf[SysStatus]))

        When("other messages are published")
        bus.publish(Packet(id, 44, 55, ParamRequestList()))
        bus.publish(Packet(id, 44, 55, RawImu()))
        Then("those events are received")
        probe.expectMsg(Packet(id, 44, 55, ParamRequestList()))
        probe.expectMsg(Packet(id, 44, 55, RawImu()))

        When("the specified messages are published")
        bus.publish(Packet(id, 44, 55, Heartbeat()))
        bus.publish(Packet(id, 44, 55, SysStatus()))
        Then("those events are not received")
        probe.expectNoMsg()

        When("other messages are published, but from a different component")
        bus.publish(Packet(id, 63, 11, RawImu()))
        Then("those events are not received")
        probe.expectNoMsg()
      }
    }

    "subscribed with multiple subscription functions" should {
      "pass all matching events" in {
        val f = fixture
        val probe = f.probe
        val bus = f.bus

        Given("an actor with multiple subscription functions")
        bus.subscribe(probe.ref, SubscribeTo.messages(classOf[Heartbeat], classOf[SysStatus]))
        bus.subscribe(probe.ref, SubscribeTo.events(classOf[ReceiveError]))

        When("any of those messages is published")
        bus.publish(Packet(id, 44, 55, Heartbeat()))
        bus.publish(Packet(id, 99, 111, SysStatus()))
        bus.publish(ReceiveError(LostPackets(41)))
        Then("those events are received")
        probe.expectMsg(Packet(id, 44, 55, Heartbeat()))
        probe.expectMsg(Packet(id, 99, 111, SysStatus()))
        probe.expectMsg(ReceiveError(LostPackets(41)))

        When("any other message is published")
        bus.publish(Packet(id, 63, 11, ParamRequestList()))
        bus.publish(SentPacket(Packet(id, 22, 245, RawImu())))
        Then("those events are not received")
        probe.expectNoMsg()
      }
    }

    "unsubscribed with a specific matcher" should {
      "pass no more events for that matcher" in {
        val f = fixture
        val probe = f.probe
        val bus = f.bus

        Given("an actor subscribed to one message")
        val matcher = SubscribeTo.message(classOf[Heartbeat])
        bus.subscribe(probe.ref, matcher)

        When("that message is published")
        bus.publish(Packet(id, 63, 11, Heartbeat()))
        Then("that event is received")
        probe.expectMsg(Packet(id, 63, 11, Heartbeat()))

        When("the actor unsubscribes")
        bus.unsubscribe(probe.ref, matcher)
        bus.publish(Packet(id, 63, 11, Heartbeat()))
        Then("that message is no longer received")
        probe.expectNoMsg()
      }

      "still pass events for other matchers" in {
        val f = fixture
        val probe = f.probe
        val bus = f.bus

        Given("an actor with two subscriptions")
        val matcher1: EventMatcher[LinkEvent] = SubscribeTo.messages(classOf[Heartbeat], classOf[SysStatus])
        val matcher2: EventMatcher[LinkEvent] = SubscribeTo.events(classOf[ReceiveError])
        bus.subscribe(probe.ref, matcher1)
        bus.subscribe(probe.ref, matcher2)

        When("any of those messages is published")
        bus.publish(Packet(id, 44, 55, Heartbeat()))
        bus.publish(Packet(id, 99, 111, SysStatus()))
        bus.publish(ReceiveError(LostPackets(41)))
        Then("those events are received")
        probe.expectMsg(Packet(id, 44, 55, Heartbeat()))
        probe.expectMsg(Packet(id, 99, 111, SysStatus()))
        probe.expectMsg(ReceiveError(LostPackets(41)))

        When("the second matcher is unsubscribed")
        bus.unsubscribe(probe.ref, matcher2)

        Then("the first type of event is still received")
        bus.publish(Packet(id, 44, 55, Heartbeat()))
        probe.expectMsg(Packet(id, 44, 55, Heartbeat()))

        And("the second type is not received")
        bus.publish(ReceiveError(LostPackets(41)))
        probe.expectNoMsg()
      }
    }

    "unsubscribed without a matcher" should {
      "pass no more events of any kind" in {
        val f = fixture
        val probe = f.probe
        val bus = f.bus

        Given("an actor with two subscriptions")
        val matcher1 = SubscribeTo.messages(classOf[Heartbeat], classOf[SysStatus])
        val matcher2 = SubscribeTo.events(classOf[ReceiveError])
        bus.subscribe(probe.ref, matcher1)
        bus.subscribe(probe.ref, matcher2)

        When("any of those events is published")
        bus.publish(Packet(id, 44, 55, Heartbeat()))
        bus.publish(Packet(id, 99, 111, SysStatus()))
        bus.publish(ReceiveError(LostPackets(41)))
        Then("those events are received")
        probe.expectMsg(Packet(id, 44, 55, Heartbeat()))
        probe.expectMsg(Packet(id, 99, 111, SysStatus()))
        probe.expectMsg(ReceiveError(LostPackets(41)))

        When("the actor is unsubscribed")
        bus.unsubscribe(probe.ref)

        And("those events are published")
        bus.publish(Packet(id, 44, 55, Heartbeat()))
        bus.publish(Packet(id, 99, 111, SysStatus()))
        bus.publish(ReceiveError(LostPackets(41)))

        Then("the events are no longer received")
        probe.expectNoMsg()
      }
    }
  }

  "compareClassifiers" should {
    "return 0 for the same matcher function" in {
      val matcher: EventMatcher[LinkEvent] = event => event.isInstanceOf[Packet]

      val bus = new LinkEventBus {
        def compare(a: Classifier, b: Classifier): Int = super.compareClassifiers(a, b)
      }

      bus.compare(matcher, matcher) shouldBe 0
    }

    "return not 0 for different matcher functions" in {
      val matcher1: EventMatcher[LinkEvent] = event => event.isInstanceOf[Packet]
      val matcher2: EventMatcher[LinkEvent] = event => event.isInstanceOf[SentPacket]

      val bus = new LinkEventBus {
        def compare(a: Classifier, b: Classifier): Int = super.compareClassifiers(a, b)
      }

      bus.compare(matcher1, matcher2) shouldNot be(0)
    }
  }
}
