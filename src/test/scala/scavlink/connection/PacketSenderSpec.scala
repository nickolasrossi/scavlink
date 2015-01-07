package scavlink.connection

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}
import scavlink.connection.frame.{DefaultMarshallerFactory, FrameTestData}
import scavlink.link.{LinkEventBus, SentPacket}
import scavlink.message.VehicleId

import scala.concurrent.duration._

class PacketSenderSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
with WordSpecLike with BeforeAndAfterAll with FrameTestData {
  def this() = this(ActorSystem("PacketSender"))
  override def afterAll() = TestKit.shutdownActorSystem(system)

  case class TestCommand(data: ByteString)

  private val events = new LinkEventBus
  events.subscribeToAll(testActor)

  def withPacketSender(initialSeq: Int)(testCode: ActorRef => Unit) = {
    val packetSender = system.actorOf(
      PacketSender.props(address, VehicleId.fromLink(address, 1), HeartbeatSettings(1, 1, Duration.Zero, 99.hours),
      events, DefaultMarshallerFactory.apply, testActor ! TestCommand(_), { case _ => }, None, initialSeq),
      "sender_" + System.currentTimeMillis())

    try {
      testCode(packetSender)
    } finally {
      system.stop(packetSender)
    }
  }



  "PacketSender actor" should {
    "convert a message into a ByteString and send it to the connection" in
      withPacketSender(29) { packetSender =>
        packetSender ! heartbeatMsg(29)
        expectMsg(TestCommand(heartbeatData(29)))
        expectMsg(SentPacket(heartbeatPacket(29)))
      }

    "send a series of messages with increasing packet numbers" in {
      withPacketSender(29) { packetSender =>
        packetSender ! heartbeatMsg(29)
        expectMsg(TestCommand(heartbeatData(29)))
        expectMsg(SentPacket(heartbeatPacket(29)))
        packetSender ! heartbeatMsg(30)
        expectMsg(TestCommand(heartbeatData(30)))
        expectMsg(SentPacket(heartbeatPacket(30)))
        packetSender ! heartbeatMsg(31)
        expectMsg(TestCommand(heartbeatData(31)))
        expectMsg(SentPacket(heartbeatPacket(31)))
      }
    }
  }
}
