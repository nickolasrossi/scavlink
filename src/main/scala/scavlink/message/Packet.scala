package scavlink.message

import scavlink.connection.frame.Sequence
import scavlink.link.LinkEvent

/**
 * Represents a MAVLink packet.
 * Sequence id is attached as an implicit val so that it doesn't take part in equality tests.
 */
case class Packet(from: From, message: Message)(implicit val seq: Sequence = Sequence.zero) extends LinkEvent {
  override def toString = s"Packet(from=${from.id} sys=${from.systemId} comp=${from.componentId} seq=$seq msgId=${message._id} msg=$message)"
}

object Packet {
  def apply(id: VehicleId, systemId: SystemId, componentId: ComponentId, message: Message): Packet =
    Packet(From(id, systemId, componentId), message)
}

case class From(id: VehicleId, systemId: SystemId, componentId: ComponentId)


/**
 * Represents a MAVLink message.
 */
trait Message {
  def _id: Int
  def _name: String
  def _bundle: Bundle
}

/**
 * Lens for messages that have a targetSystem field, so the GCS can aim a sent message at a particular system.
 */
trait TargetSystem[M <: Message] {
  _: Message =>
  def targetSystem: SystemId
  def setTargetSystem(systemId: SystemId): M
}

/**
 * Lens for messages that have a targetComponent field, so the GCS can aim a sent message at a particular component.
 */
trait TargetComponent[M <: Message] extends TargetSystem[M] {
  _: Message =>
  val targetComponent: ComponentId
  def setTargetComponent(componentId: ComponentId): M
  def setTarget(systemId: SystemId, componentId: ComponentId): M
}

/**
 * Convenience for extracting unsigned values from message fields.
 */
object Unsigned {
  def apply(b: Byte): Int = b & 0xff
  def apply(s: Short): Int = s & 0xffff
  def apply(i: Int): Long = i & 0xffffffff
}
