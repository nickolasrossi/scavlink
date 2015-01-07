package scavlink.link

import scavlink.message.enums.MavType
import scavlink.message.enums.MavType._

/**
 * Helpful properties derived from the [[MavType]] value.
 * @author Nick Rossi
 */
trait VehicleType {
  def name: String
  def isAir: Boolean
  def isGround: Boolean
  def isWater: Boolean
  def isUnderwater: Boolean

  def isRotor: Boolean
  def isFixedWing: Boolean

  def is(typ: String): Boolean = typ match {
    case "air" => isAir
    case "ground" => isGround
    case "water" => isWater
    case "underwater" => isUnderwater
    case "rotor" => isRotor
    case "fixedwing" => isFixedWing
  }

  def is(typ: Seq[String]): Boolean = typ.exists(is)
}

object VehicleType {
  val airVehicles = List(FIXED_WING, QUADROTOR, COAXIAL, HELICOPTER, AIRSHIP, FREE_BALLOON,
                         ROCKET, HEXAROTOR, OCTOROTOR, TRICOPTER, FLAPPING_WING, KITE)

  val rotorVehicles = List(QUADROTOR, COAXIAL, HELICOPTER, HELICOPTER, OCTOROTOR, TRICOPTER)

  def apply(t: MavType.Value): VehicleType = new VehicleType {
    val name: String = t.toString
    val isAir: Boolean = airVehicles.contains(t)
    val isWater: Boolean = t == SURFACE_BOAT || t == SUBMARINE
    val isGround: Boolean = t == GROUND_ROVER
    val isUnderwater: Boolean = t == SUBMARINE
    val isFixedWing: Boolean = t == FIXED_WING
    val isRotor: Boolean = rotorVehicles.contains(t)
  }
}