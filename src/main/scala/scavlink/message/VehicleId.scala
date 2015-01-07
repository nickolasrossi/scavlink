package scavlink.message

import scavlink.message.enums.MavComponent

/**
 * Uniquely identifies a vehicle in the world.
 *
 * If autopilots exposed a unique device id like a MAC address, we'd use that here.
 * Unfortunately, we have to generate one from the communication link address plus MAVLink system id.
 * Since these values could change for a given vehicle (for example, connect to it on a different port
 * or reconfigure its system id) it isn't a permanent id.
 */
case class VehicleId(address: String) {
  override def toString = address
}

object VehicleId {
  def fromLink(linkAddress: String, systemId: SystemId): VehicleId = VehicleId(s"$linkAddress#$systemId")

  // value for the local library instance
  val GroundControl = VehicleId("GroundControl")
}


class SystemId private(val id: Int) extends AnyVal {
  def toByte = id.toByte
  override def toString = id.toString
}

object SystemId {
  def apply(value: Int): SystemId = {
    require(value >= 0 && value <= 255)
    new SystemId(value)
  }

  def apply(value: Byte): SystemId = apply(Unsigned(value))
  def apply(value: String): SystemId = apply(Integer.parseInt(value))

  val zero = apply(0)
  val GroundControl = apply(255)
}


class ComponentId private(val id: Int) extends AnyVal {
  def toByte = id.toByte
  override def toString = id.toString
}

object ComponentId {
  def apply(value: Int): ComponentId = {
    require(value >= 0 && value <= 255)
    new ComponentId(value)
  }

  def apply(value: Byte): ComponentId = apply(Unsigned(value))
  def apply(value: String): ComponentId = apply(Integer.parseInt(value))
  def apply(value: MavComponent.Value): ComponentId = apply(value.id)

  val zero = apply(0)
  val GroundControl = zero
}
