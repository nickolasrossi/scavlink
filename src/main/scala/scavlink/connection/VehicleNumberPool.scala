package scavlink.connection

import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.atomic.AtomicInteger

/**
 * Number used as an alternate MAVLink system id when packets are bridged to another connection.
 */
case class VehicleNumber(number: Int, group: Int, systemId: Int)

object VehicleNumber {
  /**
   * Local vehicle numbers are divided into groups, because MAVLink system id is encoded as a byte,
   * and we can't set it higher than than 256. Also, the value 255 is reserved for ground control stations
   * (as well as a few numbers below that, so we set our top end to 250).
   * 
   * So in order to bridge packets more than 250 vehicles, we need multiple output ports, each one
   * delivering packets for a gorup of 250.
   */
  val GroupSize = 250
  
  def apply(number: Int): VehicleNumber = {
    require(number > 0)
    VehicleNumber(number, (number - 1) / GroupSize + 1, (number - 1) % GroupSize + 1)
  }

  def apply(group: Int, systemId: Int): VehicleNumber = {
    require(group > 0)
    require(systemId > 0 && systemId <= GroupSize)
    VehicleNumber((group - 1) * GroupSize + systemId, group, systemId)
  }
}

/**
 * Hand out the next available vehicle number when a new vehicle is detected.
 * The number is used as an alternate MAVLink system id when packets are bridged to another connection.
 */
trait VehicleNumberPool {
  private val top = new AtomicInteger(0)
  private val pool = new ConcurrentSkipListSet[Integer]

  def next(): Int = {
    val id = pool.pollFirst()
    if (id != null) id else top.incrementAndGet()
  }

  def free(id: Int): Unit = pool.add(id)
}

object VehicleNumberPool extends VehicleNumberPool
