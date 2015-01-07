package scavlink.connection.marshal

import scavlink.message.common.Heartbeat
import scavlink.message.{Bundle, Message}
import akka.util.{ByteIterator, ByteStringBuilder}

/**
 * A bare-bones marshaller that only knows how to convert a Heartbeat message.
 */
object HeartbeatMarshaller extends BundleMarshaller {
  val bundle = Bundle.common

  def magic = Map(0 -> 50)

  def _marshal(message: Message)(implicit builder: ByteStringBuilder) = message match {
    case Heartbeat(a, b, c, d, e, f) => _uint32_t(d); _uint8_t(a); _uint8_t(b); _uint8_t(c); _uint8_t(e); _uint8_t(f)
  }

  def _unmarshal(msgId: Int)(implicit iter: ByteIterator) = msgId match {
    case 0 => Heartbeat(customMode = uint32_t, `type` = uint8_t, autopilot = uint8_t, baseMode = uint8_t, systemStatus = uint8_t, mavlinkVersion = uint8_t)
  }
}
