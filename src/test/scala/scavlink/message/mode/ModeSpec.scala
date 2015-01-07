package scavlink.message.mode

import scavlink.message.Mode
import scavlink.message.enums.MavType
import org.scalatest.{Matchers, WordSpec}

import scala.util.Try

class ModeSpec extends WordSpec with Matchers {
  "the Mode object" should {
    "look up a mode from a string name" in {
      Mode("Auto") shouldBe Mode.Auto
      Mode("Guided") shouldBe Mode.Guided
      Mode("Loiter") shouldBe Mode.Loiter
      Mode("ReturnToLaunch") shouldBe Mode.ReturnToLaunch
      assert(Try(Mode("jfwjwefo")).isFailure)
    }

    "look up a mode from a numeric value" in {
      Mode.from(MavType.QUADROTOR, 6) shouldBe Some(Mode.ReturnToLaunch)
      Mode.from(MavType.ANTENNA_TRACKER, 1) shouldBe None
    }
  }
}
