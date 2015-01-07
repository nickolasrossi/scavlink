package scavlink.link

import scavlink.message.enums.MavParamType
import scavlink.message.enums.MavParamType._

import scala.language.implicitConversions

package object parameter {
  type Parameters = Map[String, Float]

  val maxParameterNameLength = 16

  // not a typo: in ParamValue messages, the -1 value is reserved for single parameter requests,
  // so 65534 is the maximum representable index number
  val maxParameterCount = 65534

  /**
   * Convert a value of possibly any type to a float and type identifier.
   */
  def valueToParam(value: AnyVal): (Float, MavParamType.Value) = value match {
    case v: Byte => (v.toFloat, INT8)
    case v: Char => (v.toFloat, UINT16)
    case v: Short => (v.toFloat, INT16)
    case v: Int => (v.toFloat, INT32)
    case v: Long => (v.toFloat, INT64)
    case v: Float => (v, REAL32)
    case v: Double => (v.toFloat, REAL64)
    case v: Boolean => (if (v) 1 else 0, INT8)
    case _ => (0, INT8)
  }
}
