package scavlink.link.channel

import org.scalatest.{Matchers, WordSpec}
import scavlink.message.common.RcChannelsOverride
import scavlink.message.{ComponentId, SystemId}

class ChannelSpec extends WordSpec with Matchers {

  val targetSystem = SystemId.zero
  val targetComponent = ComponentId.zero

  "rcMessage" should {
    "convert a map of Int to RC values into an RcChannelsOverride message" in {
      val message = channelOverrideMessage(targetSystem, targetComponent,
        Map[Int, Double](1 -> 24.3, 2 -> 11.45, 3 -> 75, 4 -> 90, 5 -> 66.223, 6 -> 58.9, 7 -> 100, 8 -> 0))
      message shouldBe RcChannelsOverride(targetSystem, targetComponent, 1243, 1114, 1750, 1900, 1662, 1589, 2000, 1000)
    }

    "make a message with -1 for unspecified values" in {
      val message = channelOverrideMessage(targetSystem, targetComponent, Map[Int, Double](1 -> 24.3, 3 -> 75, 6 -> 58.9))
      message shouldBe RcChannelsOverride(targetSystem, targetComponent, 1243, -1, 1750, -1, -1, 1589, -1, -1)
    }

    "refuse out-of-bound keys" in {
      an[IllegalArgumentException] should be thrownBy ChannelOverrides(Map[Int, Double](1 -> 99, 9 -> 32))
    }

    "refuse values too high " in {
      an[IllegalArgumentException] should be thrownBy ChannelOverrides(Map[Int, Double](1 -> 99, 3 -> 102))
    }
  }
}
