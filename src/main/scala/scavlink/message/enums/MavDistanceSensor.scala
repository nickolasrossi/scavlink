// Code generated by sbt-mavgen. Manual edits will be overwritten
package scavlink.message.enums

/**
 * Enumeration of distance sensor types
 */
object MavDistanceSensor extends Enumeration {
  /**
   * Laser altimeter, e.g. LightWare SF02/F or PulsedLight units
   */
  val LASER = Value(0)
  /**
   * Ultrasound altimeter, e.g. MaxBotix units
   */
  val ULTRASOUND = Value(1)
}
