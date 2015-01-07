package scavlink.connection.frame

import org.scalatest.{Matchers, WordSpec}

class SequenceSpec extends WordSpec with Matchers {
  "a Sequence" should {
    "wrap around above 255" in {
      val s = Sequence(254)
      s.next shouldBe Sequence(255)
      s + 3 shouldBe Sequence(1)
      s + 900 shouldBe Sequence(130)
    }

    "wrap around below 0" in {
      val s = Sequence(1)
      s.prev shouldBe Sequence.zero
      s - 3 shouldBe Sequence(254)
      s - 300 shouldBe Sequence(213)
    }

    "compute a difference" in {
      val s = Sequence(10)
      s.compare(Sequence(4)) shouldBe 6
      s.compare(Sequence(14)) shouldBe -4
      assert(s > Sequence(6))
      assert(s < Sequence(14))
    }

    "compare across wrap-around" in {
      val s1 = Sequence(250)
      val s2 = Sequence(6)
      assert(s1 < s2)
      assert(s2 > s1)
    }
  }
}
