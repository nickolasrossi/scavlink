package scavlink.connection

import akka.actor.Actor.Receive
import akka.actor.{Actor, Props}
import akka.util.ByteString
import com.codahale.metrics.MetricRegistry
import scavlink.connection.frame.{FrameSender, Sequence}
import scavlink.connection.marshal.{MarshallerFactory, MessageMarshaller}
import scavlink.link.{LinkEventBus, SentPacket, SubscribeTo}
import scavlink.message._
import scavlink.message.common.Heartbeat
import scavlink.message.enums.{MavAutopilot, MavType}

import scala.concurrent.duration.Duration

object PacketSender {
  def props(address: String,
            thisVehicleId: VehicleId,
            settings: HeartbeatSettings,
            events: LinkEventBus,
            marshallerFactory: MarshallerFactory,
            writeData: ByteString => Unit,
            fallback: Receive,
            metrics: Option[MetricRegistry] = None,
            initialSeq: Int = 0): Props =
    Props(classOf[PacketSender], address, thisVehicleId, settings, events, marshallerFactory, writeData, fallback, metrics, initialSeq)
}

/**
 * Writes packets to a connection.
 * Writes are handled in a separate actor from reads to prevent one from crowding out the other.
 * @author Nick Rossi
 */
class PacketSender(address: String,
                   thisVehicleId: VehicleId,
                   settings: HeartbeatSettings,
                   events: LinkEventBus,
                   marshallerFactory: MarshallerFactory,
                   writeData: ByteString => Unit,
                   fallback: Receive,
                   metrics: Option[MetricRegistry],
                   initialSeq: Int)
  extends Actor {

  import context.dispatcher

  private val tx = new FrameSender(settings.thisSystemId, settings.thisComponentId, Sequence(initialSeq))

  private var marshallers: Map[SystemId, MessageMarshaller] =
    Map.empty.withDefaultValue(marshallerFactory(MavAutopilot.ARDUPILOTMEGA))

  private val sentBytes = metrics.map(_.meter(s"$address sentBytes"))
  private val sentPackets = metrics.map(_.meter(s"$address sentPackets"))


  override def preStart() = {
    events.subscribe(self, SubscribeTo.message(classOf[Heartbeat]))

    val interval = settings.interval
    if (interval.length > 0) {
      val heartbeat = Heartbeat(MavType.GCS.id.toByte, MavAutopilot.INVALID.id.toByte, 0, 0, 0, 3)
      context.system.scheduler.schedule(Duration.Zero, interval, self, heartbeat)
    }
  }

  override def postStop() = events.unsubscribe(self)

  def receive: Receive = message orElse fallback

  private def message: Receive = {
    case m: Message => sendMessage(m)

    case Packet(From(_, systemId, _), Heartbeat(_, autopilot, _, _, _, _)) =>
      marshallers += systemId -> marshallerFactory(MavAutopilot(autopilot))
  }

  def sendMessage(message: Message): Unit = {
    val systemId = message match {
      case m: TargetSystem[_] => SystemId(m.targetSystem)
      case _ => settings.thisSystemId
    }

    val data = tx.nextMessage(marshallers(systemId))(message)
    writeData(data)

    // construct packet object for event
    val seq = Unsigned(data(2))
    val packet = Packet(From(thisVehicleId, settings.thisSystemId, settings.thisComponentId), message)(Sequence(seq))
    events.publish(SentPacket(packet))

    sentPackets.foreach(_.mark())
    sentBytes.foreach(_.mark(data.length))
  }
}
