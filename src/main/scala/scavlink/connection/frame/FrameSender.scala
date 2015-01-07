package scavlink.connection.frame

import scavlink.connection.marshal.MessageMarshaller
import scavlink.message._
import akka.util.{ByteString, ByteStringBuilder}
import org.slf4j.LoggerFactory

/**
 * Turns messages into frames ready to be sent over a connection.
 * Stateful on the packet sequence number. An instance of this class should be used by only one sender actor.
 * See [[http://en.wikipedia.org/wiki/MAVLink]]
 * @author Nick Rossi
 */
class FrameSender(val systemId: SystemId = SystemId.GroundControl,
                  val componentId: ComponentId = ComponentId.zero,
                  initialSeq: Sequence = Sequence.zero) extends Framing {

  private val logger = LoggerFactory.getLogger(this.getClass)

  // packet sequence counter, rolls over at 255
  private var sequence = initialSeq

  def nextMessage(marshaller: MessageMarshaller)(message: Message): ByteString =  {
    val payload = marshaller.marshal(message)

    val bb = new ByteStringBuilder
    bb.putByte(FrameStart)
    bb.putByte(payload.length.toByte)
    bb.putByte(sequence.value.toByte)
    bb.putByte(systemId.toByte)
    bb.putByte(componentId.toByte)
    bb.putByte(message._id.toByte)
    bb.append(payload)
    val frame = bb.result()
    val crc = computeCrc(frame.iterator.drop(1), marshaller.magic(message._id))
    val (crcLo, crcHi) = crcBytes(crc)

    sequence = sequence.next
    frame ++ ByteString(crcLo, crcHi)
  }
}
