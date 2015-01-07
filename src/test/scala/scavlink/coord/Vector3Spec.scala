package scavlink.coord

import org.scalatest.{Matchers, WordSpec}
import spire.implicits._

class Vector3Spec extends WordSpec with Matchers {
  val v = Vector3(1, 2, 3)
  val w = Vector3(4, 5, 6)

  "a Vector3" should {
    "perform a dot product" in {
      v.dot(w) shouldBe 32
    }

    "perform a cross product" in {
      v.cross(w) shouldBe Vector3(-3, 6, -3)
    }

    "compute length" in {
      v.length shouldBe math.sqrt(14)
    }
  }
}
