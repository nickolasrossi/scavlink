package scavlink.connection

import java.nio.ByteOrder

import akka.util.{ByteIterator, ByteStringBuilder}
import scavlink.message.enums.MavAutopilot

import scala.language.implicitConversions

/**
 * Conversions between message fields and byte sequences.
 *
 * The method names and implicit parameters here make it easy for the code generator to spit out
 * marshalling function calls in the Marshaller and Unmarshaller classes.
 *
 * For marshalling, string and array fields are truncated or padded with zeros as necessary to match
 * the field length specified in the protocol.
 */
package object marshal {
  type MarshallerFactory = MavAutopilot.Value => MessageMarshaller

  implicit val order = ByteOrder.LITTLE_ENDIAN

  implicit def IntToByte(x: Int): Byte = x.toByte
  implicit def IntToShort(x: Int): Short = x.toShort
  implicit def IntToLong(x: Int): Long = x.toLong
//  implicit def ComponentToTuple(x: Component): (Byte, Byte) = (x.systemId.toByte, x.componentId.toByte)
//  implicit def TupleToComponent(x: (Byte, Byte)): Component = Component(Unsigned(x._1), Unsigned(x._2))

  // unmarshal
  def char(implicit iter: ByteIterator): Char = iter.getByte.toChar
  def float(implicit iter: ByteIterator): Float = iter.getFloat
  def double(implicit iter: ByteIterator): Double = iter.getDouble
  def int8_t(implicit iter: ByteIterator): Byte = iter.getByte
  def int16_t(implicit iter: ByteIterator): Short = iter.getShort
  def int32_t(implicit iter: ByteIterator): Int = iter.getInt
  def int64_t(implicit iter: ByteIterator): Long = iter.getLong
  def uint8_t(implicit iter: ByteIterator) = int8_t
  def uint16_t(implicit iter: ByteIterator) = int16_t
  def uint32_t(implicit iter: ByteIterator) = int32_t
  def uint64_t(implicit iter: ByteIterator) = int64_t

  def float_2(implicit iter: ByteIterator) = (float, float)
  def double_2(implicit iter: ByteIterator) = (double, double)
  def int8_t_2(implicit iter: ByteIterator) = (int8_t, int8_t)
  def int16_t_2(implicit iter: ByteIterator) = (int16_t, int16_t)
  def int32_t_2(implicit iter: ByteIterator) = (int32_t, int32_t)
  def int64_t_2(implicit iter: ByteIterator) = (int64_t, int64_t)
  def uint8_t_2(implicit iter: ByteIterator) = (uint8_t, uint8_t)
  def uint16_t_2(implicit iter: ByteIterator) = (uint16_t, uint16_t)
  def uint32_t_2(implicit iter: ByteIterator) = (uint32_t, uint32_t)
  def uint64_t_2(implicit iter: ByteIterator) = (uint64_t, uint64_t)

  def float_3(implicit iter: ByteIterator) = (float, float, float)
  def double_3(implicit iter: ByteIterator) = (double, double, double)
  def int8_t_3(implicit iter: ByteIterator) = (int8_t, int8_t, int8_t)
  def int16_t_3(implicit iter: ByteIterator) = (int16_t, int16_t, int16_t)
  def int32_t_3(implicit iter: ByteIterator) = (int32_t, int32_t, int32_t)
  def int64_t_3(implicit iter: ByteIterator) = (int64_t, int64_t, int64_t)
  def uint8_t_3(implicit iter: ByteIterator) = (uint8_t, uint8_t, uint8_t)
  def uint16_t_3(implicit iter: ByteIterator) = (uint16_t, uint16_t, uint16_t)
  def uint32_t_3(implicit iter: ByteIterator) = (uint32_t, uint32_t, uint32_t)
  def uint64_t_3(implicit iter: ByteIterator) = (uint64_t, uint64_t, uint64_t)

  def float_4(implicit iter: ByteIterator) = (float, float, float, float)
  def double_4(implicit iter: ByteIterator) = (double, double, double, double)
  def int8_t_4(implicit iter: ByteIterator) = (int8_t, int8_t, int8_t, int8_t)
  def int16_t_4(implicit iter: ByteIterator) = (int16_t, int16_t, int16_t, int16_t)
  def int32_t_4(implicit iter: ByteIterator) = (int32_t, int32_t, int32_t, int32_t)
  def int64_t_4(implicit iter: ByteIterator) = (int64_t, int64_t, int64_t, int64_t)
  def uint8_t_4(implicit iter: ByteIterator) = (uint8_t, uint8_t, uint8_t, uint8_t)
  def uint16_t_4(implicit iter: ByteIterator) = (uint16_t, uint16_t, uint16_t, uint16_t)
  def uint32_t_4(implicit iter: ByteIterator) = (uint32_t, uint32_t, uint32_t, uint32_t)
  def uint64_t_4(implicit iter: ByteIterator) = (uint64_t, uint64_t, uint64_t, uint64_t)

  def float_(n: Int)(implicit iter: ByteIterator) = Vector.fill(n)(float)
  def double_(n: Int)(implicit iter: ByteIterator) = Vector.fill(n)(double)
  def int8_t_(n: Int)(implicit iter: ByteIterator) = Vector.fill(n)(int8_t)
  def int16_t_(n: Int)(implicit iter: ByteIterator) = Vector.fill(n)(int16_t)
  def int32_t_(n: Int)(implicit iter: ByteIterator) = Vector.fill(n)(int32_t)
  def int64_t_(n: Int)(implicit iter: ByteIterator) = Vector.fill(n)(int64_t)
  def uint8_t_(n: Int)(implicit iter: ByteIterator) = Vector.fill(n)(uint8_t)
  def uint16_t_(n: Int)(implicit iter: ByteIterator) = Vector.fill(n)(uint16_t)
  def uint32_t_(n: Int)(implicit iter: ByteIterator) = Vector.fill(n)(uint32_t)
  def uint64_t_(n: Int)(implicit iter: ByteIterator) = Vector.fill(n)(uint64_t)

  def char_(n: Int)(implicit iter: ByteIterator) = {
    val bytes = new Array[Byte](n)
    iter.getBytes(bytes)
    val terminator = bytes.indexOf(0: Byte)
    if (terminator >= 0) new String(bytes, 0, terminator) else new String(bytes)
  }


  // marshal
  def _char(x: Char)(implicit bb: ByteStringBuilder) = bb.putByte(x.toByte)
  def _float(x: Float)(implicit bb: ByteStringBuilder) = bb.putFloat(x)
  def _double(x: Double)(implicit bb: ByteStringBuilder) = bb.putDouble(x)
  def _int8_t(x: Byte)(implicit bb: ByteStringBuilder) = bb.putByte(x)
  def _int16_t(x: Short)(implicit bb: ByteStringBuilder) = bb.putShort(x)
  def _int32_t(x: Int)(implicit bb: ByteStringBuilder) = bb.putInt(x)
  def _int64_t(x: Long)(implicit bb: ByteStringBuilder) = bb.putLong(x)
  def _uint8_t(x: Byte)(implicit bb: ByteStringBuilder) = _int8_t(x)
  def _uint16_t(x: Short)(implicit bb: ByteStringBuilder) = _int16_t(x)
  def _uint32_t(x: Int)(implicit bb: ByteStringBuilder) = bb.putInt(x.toInt)
  def _uint64_t(x: Long)(implicit bb: ByteStringBuilder) = _int64_t(x)

  def _float_2(x: (Float, Float))(implicit bb: ByteStringBuilder) = { _float(x._1); _float(x._2) }
  def _double_2(x: (Double, Double))(implicit bb: ByteStringBuilder) = { _double(x._1); _double(x._2) }
  def _int8_t_2(x: (Byte, Byte))(implicit bb: ByteStringBuilder) = { _int8_t(x._1); _int8_t(x._2) }
  def _int16_t_2(x: (Short, Short))(implicit bb: ByteStringBuilder) = { _int16_t(x._1); _int16_t(x._2) }
  def _int32_t_2(x: (Int, Int))(implicit bb: ByteStringBuilder) = { _int32_t(x._1); _int32_t(x._2) }
  def _int64_t_2(x: (Long, Long))(implicit bb: ByteStringBuilder) = { _int64_t(x._1); _int64_t(x._2) }
  def _uint8_t_2(x: (Byte, Byte))(implicit bb: ByteStringBuilder) = { _uint8_t(x._1); _uint8_t(x._2) }
  def _uint16_t_2(x: (Short, Short))(implicit bb: ByteStringBuilder) = { _uint16_t(x._1); _uint16_t(x._2) }
  def _uint32_t_2(x: (Int, Int))(implicit bb: ByteStringBuilder) = { _uint32_t(x._1); _uint32_t(x._2) }
  def _uint64_t_2(x: (Long, Long))(implicit bb: ByteStringBuilder) = { _uint64_t(x._1); _uint64_t(x._2) }

  def _float_3(x: (Float, Float, Float))(implicit bb: ByteStringBuilder) = { _float(x._1); _float(x._2); _float(x._3) }
  def _double_3(x: (Double, Double, Double))(implicit bb: ByteStringBuilder) = { _double(x._1); _double(x._2); _double(x._3) }
  def _int8_t_3(x: (Byte, Byte, Byte))(implicit bb: ByteStringBuilder) = { _int8_t(x._1); _int8_t(x._2); _int8_t(x._3) }
  def _int16_t_3(x: (Short, Short, Short))(implicit bb: ByteStringBuilder) = { _int16_t(x._1); _int16_t(x._2); _int16_t(x._3) }
  def _int32_t_3(x: (Int, Int, Int))(implicit bb: ByteStringBuilder) = { _int32_t(x._1); _int32_t(x._2); _int32_t(x._3) }
  def _int64_t_3(x: (Long, Long, Long))(implicit bb: ByteStringBuilder) = { _int64_t(x._1); _int64_t(x._2); _int64_t(x._3) }
  def _uint8_t_3(x: (Byte, Byte, Byte))(implicit bb: ByteStringBuilder) = { _uint8_t(x._1); _uint8_t(x._2); _uint8_t(x._3) }
  def _uint16_t_3(x: (Short, Short, Short))(implicit bb: ByteStringBuilder) = { _uint16_t(x._1); _uint16_t(x._2); _uint16_t(x._3) }
  def _uint32_t_3(x: (Int, Int, Int))(implicit bb: ByteStringBuilder) = { _uint32_t(x._1); _uint32_t(x._2); _uint32_t(x._3) }
  def _uint64_t_3(x: (Long, Long, Long))(implicit bb: ByteStringBuilder) = { _uint64_t(x._1); _uint64_t(x._2); _uint64_t(x._3) }

  def _float_4(x: (Float, Float, Float, Float))(implicit bb: ByteStringBuilder) = { _float(x._1); _float(x._2); _float(x._3); _float(x._4) }
  def _double_4(x: (Double, Double, Double, Double))(implicit bb: ByteStringBuilder) = { _double(x._1); _double(x._2); _double(x._3); _double(x._4) }
  def _int8_t_4(x: (Byte, Byte, Byte, Byte))(implicit bb: ByteStringBuilder) = { _int8_t(x._1); _int8_t(x._2); _int8_t(x._3); _int8_t(x._4) }
  def _int16_t_4(x: (Short, Short, Short, Short))(implicit bb: ByteStringBuilder) = { _int16_t(x._1); _int16_t(x._2); _int16_t(x._3); _int16_t(x._4) }
  def _int32_t_4(x: (Int, Int, Int, Int))(implicit bb: ByteStringBuilder) = { _int32_t(x._1); _int32_t(x._2); _int32_t(x._3); _int32_t(x._4) }
  def _int64_t_4(x: (Long, Long, Long, Long))(implicit bb: ByteStringBuilder) = { _int64_t(x._1); _int64_t(x._2); _int64_t(x._3); _int64_t(x._4) }
  def _uint8_t_4(x: (Byte, Byte, Byte, Byte))(implicit bb: ByteStringBuilder) = { _uint8_t(x._1); _uint8_t(x._2); _uint8_t(x._3); _uint8_t(x._4) }
  def _uint16_t_4(x: (Short, Short, Short, Short))(implicit bb: ByteStringBuilder) = { _uint16_t(x._1); _uint16_t(x._2); _uint16_t(x._3); _uint16_t(x._4) }
  def _uint32_t_4(x: (Int, Int, Int, Int))(implicit bb: ByteStringBuilder) = { _uint32_t(x._1); _uint32_t(x._2); _uint32_t(x._3); _uint32_t(x._4) }
  def _uint64_t_4(x: (Long, Long, Long, Long))(implicit bb: ByteStringBuilder) = { _uint64_t(x._1); _uint64_t(x._2); _uint64_t(x._3); _uint64_t(x._4) }

  def _float_(n: Int)(x: Vector[Float])(implicit bb: ByteStringBuilder) = (if (x.size >= n) x.take(n) else x.padTo(n, 0f)).foreach(_float)
  def _double_(n: Int)(x: Vector[Double])(implicit bb: ByteStringBuilder) = (if (x.size >= n) x.take(n) else x.padTo(n, 0d)).foreach(_double)
  def _int8_t_(n: Int)(x: Vector[Byte])(implicit bb: ByteStringBuilder) = (if (x.size >= n) x.take(n) else x.padTo(n, 0: Byte)).foreach(_int8_t)
  def _int16_t_(n: Int)(x: Vector[Short])(implicit bb: ByteStringBuilder) = (if (x.size >= n) x.take(n) else x.padTo(n, 0: Short)).foreach(_int16_t)
  def _int32_t_(n: Int)(x: Vector[Int])(implicit bb: ByteStringBuilder) = (if (x.size >= n) x.take(n) else x.padTo(n, 0)).foreach(_int32_t)
  def _int64_t_(n: Int)(x: Vector[Long])(implicit bb: ByteStringBuilder) = (if (x.size >= n) x.take(n) else x.padTo(n, 0L)).foreach(_int64_t)
  def _uint8_t_(n: Int)(x: Vector[Byte])(implicit bb: ByteStringBuilder) = (if (x.size >= n) x.take(n) else x.padTo(n, 0: Byte)).foreach(_uint8_t)
  def _uint16_t_(n: Int)(x: Vector[Short])(implicit bb: ByteStringBuilder) = (if (x.size >= n) x.take(n) else x.padTo(n, 0: Short)).foreach(_uint16_t)
  def _uint32_t_(n: Int)(x: Vector[Int])(implicit bb: ByteStringBuilder) = (if (x.size >= n) x.take(n) else x.padTo(n, 0)).foreach(_uint32_t)
  def _uint64_t_(n: Int)(x: Vector[Long])(implicit bb: ByteStringBuilder) = (if (x.size >= n) x.take(n) else x.padTo(n, 0L)).foreach(_uint64_t)

  def _char_(n: Int)(x: String)(implicit bb: ByteStringBuilder) = bb.putBytes(x.getBytes.take(n).padTo(n, 0: Byte))
}
