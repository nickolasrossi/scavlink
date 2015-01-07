package scavlink.link.operation

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{TestKit, TestProbe}
import akka.util.Timeout
import org.scalatest.{BeforeAndAfterAll, GivenWhenThen, Matchers, WordSpecLike}
import scavlink.connection.frame.DefaultMarshallerFactory
import scavlink.connection.{VehicleNumber, HeartbeatSettings, PacketSender}
import scavlink.link._
import scavlink.message.enums.{MavAutopilot, MavType}
import scavlink.message.{ComponentId, SystemId, VehicleId}
import scavlink.settings.ScavlinkConfig

import scala.concurrent.duration._
import scala.util.Random

abstract class VehicleOpSpecSupport(_system: ActorSystem) extends TestKit(_system)
with WordSpecLike with Matchers with BeforeAndAfterAll with GivenWhenThen {

  val address = "mock"
  val events = new LinkEventBus
  val random = new Random(System.currentTimeMillis())
  val settings = HeartbeatSettings(SystemId.GroundControl, ComponentId.zero, Duration.Zero, 99.hours)
  val id = VehicleId.fromLink(address, SystemId(1))
  val systemId = SystemId(1)
  val componentId = ComponentId(1)
  val vehicleInfo = VehicleInfo(id, VehicleNumber(systemId), systemId, componentId, MavType.GENERIC, MavAutopilot.GENERIC, None)
  implicit val timeout = Timeout(1.minute)

  val probe = new TestProbe(system)
  val packetSender = system.actorOf(
    PacketSender.props(address, VehicleId.GroundControl, settings, events, DefaultMarshallerFactory.apply,
    probe.ref ! _, { case _ => }), "sender_" + System.currentTimeMillis())
  val link = new Link("test", events, new ScavlinkConfig(system.settings.config, "scavlink"), packetSender, None)
  val vehicle = new Vehicle(vehicleInfo, link, Nil)(system)

  val printActor = system.actorOf(Props[PacketPrinter], "print")
  events.subscribeToAll(printActor)

  override def afterAll() = TestKit.shutdownActorSystem(system)


  def withResponder(props: Props)(testCode: => Unit): Unit = {
    // responds to SentPacket events as if it were a vehicle
    val responseActor = system.actorOf(props, "MockVehicle_" + random.nextInt(Int.MaxValue) + "_" + System.currentTimeMillis())
    link.events.subscribe(responseActor, SubscribeTo.event(classOf[SentPacket]))

    try {
      testCode
    } finally {
      link.events.unsubscribe(responseActor)
      system.stop(responseActor)
    }
  }

  def withActor[T](actorType: Class[T])(testCode: ActorRef => Any): Unit = {
    val actor = system.actorOf(Props(actorType, vehicle),
      "OpActor_" + random.nextInt(Int.MaxValue).toString + "_" + System.currentTimeMillis())

    try {
      testCode(actor)
    } finally {
      system.stop(actor)
    }
  }
}
