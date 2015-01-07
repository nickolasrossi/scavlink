package scavlink.link

import scavlink.message.common.RcChannelsOverride
import scavlink.message.{ComponentId, SystemId}

package object channel {
  /**
   * Builds the channel override message from a set of channel values.
   * @param values one or more channels between 1 and 8 mapped to values 0-100
   * @return channel override MAVLink message
   */
  def channelOverrideMessage(targetSystem: SystemId, targetComponent: ComponentId, values: Map[Int, Double]): RcChannelsOverride = {
    def unscale(value: Double): Short = ((value * 10) + 1000).toShort
    val rc = values.mapValues(unscale).withDefaultValue(-1.toShort)
    RcChannelsOverride(targetSystem, targetComponent, rc(1), rc(2), rc(3), rc(4), rc(5), rc(6), rc(7), rc(8))
  }


  def requireChannelValue(channel: Int, value: Double): Unit = {
    require(channel >= 1 && channel <= 8, s"Channel number must be 1-8 (was $channel)")
    require(value >= 0 && value <= 100, s"Channel value must be 0-100 (was $channel -> $value)")
  }
}
