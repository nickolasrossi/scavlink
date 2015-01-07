package scavlink.connection.frame

import akka.util.ByteIterator

/**
 * Logic common to frame sender and receiver.
 */
trait Framing {
  val FrameStart = 0xfe.toByte
  val EmptyPacketLength = 8

  /**
   * Compute X25 CRC for a list of bytes.
   */
  def computeCrc(data: ByteIterator, magic: Int): Int = {
    val crc = data.foldLeft(0xffff)(x25acc)
    x25acc(crc, magic.toByte)
  }

  /**
   * Convert crc to lo/hi bytes.
   */
  def crcBytes(crc: Int): (Byte, Byte) = ((crc & 0xff).toByte, (crc >> 8).toByte)

  /**
   * Accumulate a single byte into a CRC value.
   */
  def x25acc(acc: Int, b: Byte): Int = {
    val v1 = unsigned(b) ^ (acc & 0xff)
    val v2 = v1 ^ ((v1 << 4) & 0xff)
    ((acc >> 8) & 0xff) ^ (v2 << 8) ^ (v2 << 3) ^ ((v2 >> 4) & 0xf)
  }

  /**
   * Return the unsigned integer value for a byte.
   */
  def unsigned(b: Byte): Int = b & 0xff
}
