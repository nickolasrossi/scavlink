package scavlink.connection

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.ByteString
import com.codahale.metrics.MetricRegistry
import org.scalatest.{BeforeAndAfterAll, GivenWhenThen, Matchers, WordSpecLike}
import scavlink.ScavlinkContext
import scavlink.connection.frame.{DefaultMarshallerFactory, FrameSender, FrameTestData}
import scavlink.link.SubscribeTo
import scavlink.message._
import scavlink.message.common.AuthKey
import scavlink.settings.ScavlinkConfig
import scala.concurrent.duration.Duration

case class Stop(id: SystemId)

class MockReceiver(val sctx: ScavlinkContext, writeData: ByteString => Unit) extends PacketReceiver with FrameTestData {

  override def preStart() = start(writeData, { case _ => })

  def receive: Receive = {
    case data: ByteString => receiveData(data)
    case Stop(id) => stopVehicle(id)
  }
}

class PacketReceiverSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
with WordSpecLike with Matchers with BeforeAndAfterAll with GivenWhenThen with FrameTestData {

  def this() = this(ActorSystem("PacketReceiver"))

  val events = new ConnectionEventBus
  val config = new ScavlinkConfig(system.settings.config, "scavlink")
  val sctx = ScavlinkContext(events, config, new MetricRegistry, Nil, None, None, DefaultMarshallerFactory.apply)

  val validKey = "12345"
  val invalidKey = "54321"

  val receiverCount = new AtomicInteger(0)

  def withReceiver(sctx: ScavlinkContext)(testCode: ActorRef => Unit) = {
    val writeData: ByteString => Unit = { x => }
    val receiver = system.actorOf(Props(classOf[MockReceiver], sctx, writeData))

    try {
      events.subscribeToAll(self)
      testCode(receiver)
    } finally {
      events.unsubscribe(self)
      system.stop(receiver)
    }
  }

  def messageMarshaller(systemId: SystemId, componentId: ComponentId): Message => ByteString = {
    val tx = new FrameSender(systemId, componentId)
    tx.nextMessage(Bundle.common.marshaller)
  }

  def authorizer(validKey: String)(key: String): Boolean = key == validKey

  def expectPacket(id: VehicleId, msg: Message) =
    expectMsgPF[Packet]() { case event@Packet(From(`id`, _, _), `msg`) => event }

  def expectVehicleUpAndHeartbeat(id: VehicleId, msg: Message) = {
    val msgs = receiveN(2)

    msgs.collect {
      case event@Packet(From(`id`, _, _), `msg`) => event
    }.size shouldBe 1

    msgs.collect {
      case event@VehicleUp(vehicle) if vehicle.id == id => event
    }.size shouldBe 1
  }


  "a PacketReceiver" should {
    "produce VehicleUp event when a new system id is received, then VehicleDown when stopped" in
      withReceiver(sctx) { receiver =>
        Given("an ordinary PacketReceiver")
        Then("LinkUp should publish as soon as it starts")
        val LinkUp(link) = expectMsgPF[LinkUp]() {
          case event@LinkUp(l) if l.address == address => event
        }

        link.events.subscribe(self, SubscribeTo.allMessages)

        val id = VehicleId.fromLink(address, 1)
        val marshal = messageMarshaller(1, 1)

        When("the first Heartbeat is received")
        val message = heartbeatMsg(1)
        receiver ! marshal(message)

        Then("the PacketReceiver should generate a VehicleUp event and a Heartbeat packet")
        expectVehicleUpAndHeartbeat(id, message)

        When("the vehicle is stopped")
        receiver ! Stop(1)

        Then("it should generate a VehicleDown event")
        expectMsgPF[VehicleDown]() {
          case event@VehicleDown(vehicle) if vehicle.id == id => event
        }

        link.events.unsubscribe(self)
      }
  }

  "Link authorization" should {
    "authorize the AuthKey before publishing LinkUp when linkAuthorizer is specified" in
      withReceiver(sctx.copy(linkAuthorizer = Some(authorizer(validKey)))) { receiver =>
        Given("a PacketRecevier with a linkAuthorizer")
        val id = VehicleId.fromLink(address, SystemId.GroundControl)
        val marshal = messageMarshaller(SystemId.GroundControl, ComponentId.GroundControl)

        Then("a message received before AuthKey should not be published")
        receiver ! marshal(heartbeatMsg(1))

        expectNoMsg()

        Given("a valid AuthKey from a GCS is received")
        receiver ! marshal(AuthKey(validKey))

        Then("LinkUp should be published")
        expectMsgPF[LinkUp]() {
          case event@LinkUp(link) /*if link.address == address*/ =>
            println(event)
            event
        }

        And("a subsequent Heartbeat message")
        val message = heartbeatMsg(2)
        receiver ! marshal(message)

        Then("the PacketReceiver should generate a VehicleUp event and a Heartbeat packet")
        expectMsgPF[VehicleUp]() {
          case event@VehicleUp(vehicle) if vehicle.id == id => event
        }
      }

    "not publish messages if invalid key is specified" in
      withReceiver(sctx.copy(linkAuthorizer = Some(authorizer(validKey)))) { receiver =>
        Given("a PacketRecevier with a linkAuthorizer")
        val id = VehicleId.fromLink(address, SystemId.GroundControl)
        val marshal = messageMarshaller(SystemId.GroundControl, ComponentId.GroundControl)

        Then("a message received before AuthKey should not be published")
        receiver ! marshal(heartbeatMsg(1))

        expectNoMsg()

        Given("an invalid AuthKey received from a GCS")
        receiver ! marshal(AuthKey(invalidKey))

        Then("no event should be published")
        expectNoMsg()

        And("a subsequent message")
        receiver ! marshal(heartbeatMsg(2))

        Then("should not be published")
        expectNoMsg()

        Given("a valid AuthKey after all of the above")
        receiver ! marshal(AuthKey(validKey))

        Then("should generate a LinkUp event")
        val LinkUp(link) = expectMsgPF[LinkUp]() {
          case event@LinkUp(l) if l.address == address => event
        }

        link.events.subscribe(self, SubscribeTo.allMessages)

        And("a subsequent Heartbeat message")
        val message = heartbeatMsg(2)
        receiver ! marshal(message)

        Then("the PacketReceiver should generate a VehicleUp event and a Heartbeat packet")
        expectVehicleUpAndHeartbeat(id, message)

        link.events.unsubscribe(self)
      }
  }

  "Vehicle authorization" should {
    "authorize key before VehicleUp if vehicleAuthorizer is specified" in
      withReceiver(sctx.copy(vehicleAuthorizer = Some(authorizer(validKey)))) { receiver =>
        Given("a PacketReceiver with a vehicleAuthorizer")
        Then("LinkUp should be published immediately")
        val LinkUp(link) = expectMsgPF[LinkUp]() {
          case event@LinkUp(l) if l.address == address => event
        }

        link.events.subscribe(self, SubscribeTo.allMessages)

        val id = VehicleId.fromLink(address, 1)
        val marshal = messageMarshaller(1, 1)

        Given("a message received before AuthKey")
        receiver ! marshal(heartbeatMsg(1))

        Then("should not be published")
        expectNoMsg()

        Given("an invalid AuthKey received from a vehicle")
        receiver ! marshal(AuthKey(invalidKey))

        Then("should not be published")
        expectNoMsg()

        Given("a valid AuthKey from the vehicle after all of the above")
        val message1 = AuthKey(validKey)
        receiver ! marshal(message1)

        Then("should generate a VehicleUp event")
        expectPacket(id, message1)

        And("a subsequent Heartbeat message")
        val message2 = heartbeatMsg(2)
        receiver ! marshal(message2)

        Then("the PacketReceiver should generate a VehicleUp event and a Heartbeat packet")
        expectVehicleUpAndHeartbeat(id, message2)

        link.events.unsubscribe(self)
      }
  }
}
