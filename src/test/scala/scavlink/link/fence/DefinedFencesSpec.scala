package scavlink.link.fence

import com.typesafe.config.ConfigFactory
import org.scalatest.{Matchers, WordSpec}

class DefinedFencesSpec extends WordSpec with Matchers with FenceTestData {
  "ConfiguredFences" should {
    "parse polygon" in {
      DefinedFences.parseFence(ConfigFactory.parseString(config1)) shouldBe fence1
      DefinedFences.parseFence(ConfigFactory.parseString(configMax80)) shouldBe fenceMax80
      DefinedFences.parseFence(ConfigFactory.parseString(configMin60Max80)) shouldBe fenceMin60Max80
    }

    "parse circle" in {
      DefinedFences.parseFence(ConfigFactory.parseString(config4)) shouldBe fenceCircle4
    }

    "parse world" in {
      DefinedFences.parseFence(ConfigFactory.parseString("upper-altitude = 80")) shouldBe worldMax80
    }

    "parse well-known text" in {
      val fence = DefinedFences.parseFence(ConfigFactory.parseString(config12))
      assert(fence.contains(pointInside1))
      assert(fence.contains(pointInside2))
    }

    "parse fences by name" in {
      val config = s"define = {\n" +
        s"fence1 = { $config1 }\n" +
        s"fenceMax80 = { $configMax80 }\n" +
        s"fenceCircle4 = { $config4 }\n" +
        s"fence12 = { $config12 }\n" +
        s"}"

      val expected = Map[String, Fence](
        "fence1" -> fence1,
        "fenceMax80" -> fenceMax80,
        "fenceCircle4" -> fenceCircle4,
        "fence12" -> DefinedFences.parseFence(ConfigFactory.parseString(config12))
      )

      val fences = DefinedFences(ConfigFactory.parseString(config))
      fences.size shouldBe 4
      assertResult(expected)(fences)
    }
  }
}
