package scavlink.connection.frame

import akka.util.ByteString
import com.codahale.metrics.MetricRegistry
import scavlink.connection.marshal.{MarshallerFactory, MessageMarshaller}
import scavlink.message.common.Heartbeat
import scavlink.message.enums.MavAutopilot
import scavlink.message._

import scala.annotation.tailrec

sealed trait FrameError {
  def frame: ByteString
}
case class CRCMismatch(provided: Int, computed: Int, frame: ByteString) extends FrameError
case class UnmarshalError(vehicle: VehicleId, error: String, msgId: Int, frame: ByteString) extends FrameError
case class UnknownMessage(vehicle: VehicleId, msgId: Int, frame: ByteString) extends FrameError
case class LostPackets(count: Int, frame: ByteString = ByteString.empty) extends FrameError

/**
 * Extracts packets from an incoming data stream.
 * Not thread safe on its own; meant to be used within an actor.
 * @see [[scavlink.connection.PacketReceiver]]
 * @see [[http://en.wikipedia.org/wiki/MAVLink]]
 * @author Nick Rossi
 */
class FrameReceiver(val address: String, val marshallerFactory: MarshallerFactory, metrics: Option[MetricRegistry] = None)
  extends Framing {
  // track which marshaller to use based on autopilot value in received Heartbeat
  private var marshallers: Map[SystemId, MessageMarshaller] =
    Map.empty.withDefaultValue(marshallerFactory(MavAutopilot.ARDUPILOTMEGA))

  // all possible VehicleId values
  private val vehicleIds = Array.tabulate(256)(id => VehicleId.fromLink(address, id))

  // current byte buffer
  private var buffer = ByteString.empty

  // meters
  private val receivedBytes = metrics.map(_.meter(s"$address receivedBytes"))
  private val receivedPackets = metrics.map(_.meter(s"$address receivedPackets"))
  private val receiveErrors = metrics.map(_.meter(s"$address receiveErrors"))

  /**
   * Append data to the byte buffer and return any completed packets.
   */
  def receivedData(data: ByteString): Seq[Either[FrameError, Packet]] = {
    receivedBytes.foreach(_.mark(data.length))
    val (newbuf, packets) = unmarshal(buffer ++ data)
    buffer = newbuf
    packets
  }

  /**
   * Extract all valid packets from the data buffer.
   * Returns the buffer pointing to the next FrameStart, or empty if one isn't found.
   */
  @tailrec
  final def unmarshal(data: ByteString, acc: Seq[Either[FrameError, Packet]] = IndexedSeq()): (ByteString, Seq[Either[FrameError, Packet]]) = {
    if (data.length < EmptyPacketLength) {
      (data, acc)
    } else if (data(0) != FrameStart) {
      unmarshal(data.dropWhile(_ != FrameStart), acc)
    } else {
      val payloadLength = unsigned(data(1))
      val frameLength = payloadLength + EmptyPacketLength
      if (data.length < frameLength) {
        (data.compact, acc)
      } else {
        val (newData, packet) = unmarshalOne(data, payloadLength, frameLength)
        packet match {
          case Left(_) => receiveErrors.foreach(_.mark)
          case Right(_) => receivedPackets.foreach(_.mark)
        }

        unmarshal(newData, acc :+ packet)
      }
    }
  }

  def unmarshalOne(data: ByteString, payloadLength: Int, frameLength: Int): (ByteString, Either[FrameError, Packet]) = {
    def frame = data.take(frameLength) // only evaluated if needed
    val seq = Sequence(unsigned(data(2)))
    val systemId = SystemId(data(3))
    val componentId = ComponentId(data(4))
    val msgId = unsigned(data(5))

    val vehicle = vehicleIds(systemId)
    val marshaller = marshallers(systemId)
    if (!marshaller.magic.isDefinedAt(msgId)) {
      return (data.drop(1), Left(UnknownMessage(vehicle, msgId, frame)))
    }

    val crc = unsigned(data(payloadLength + 6)) + (unsigned(data(payloadLength + 7)) << 8)
    val crcData = data.iterator.slice(1, payloadLength + 6)
    val computed = computeCrc(crcData, marshaller.magic(msgId))
    if (crc != computed) {
      return (data.drop(1), Left(CRCMismatch(crc, computed, frame)))
    }

    try {
      val payload = data.iterator.slice(6, payloadLength + 6)
      val message = marshaller.unmarshal(msgId, payload)
      val packet = Packet(From(vehicle, systemId, componentId), message)(seq)

      // if message is a Heartbeat, set the current component's marshaller from the autopilot value
      message match {
        case msg: Heartbeat =>
          val newMarshaller = marshallerFactory(MavAutopilot(msg.autopilot))
          if (newMarshaller != marshaller) marshallers += systemId -> newMarshaller

        case _ => //
      }

      (data.drop(frameLength), Right(packet))
    } catch {
      case e: Exception => (data.drop(1), Left(UnmarshalError(vehicle, e.getMessage, msgId, frame)))
    }
  }
}
