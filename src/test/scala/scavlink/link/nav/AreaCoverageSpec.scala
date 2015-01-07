package scavlink.link.nav

import scavlink.coord.LatLon
import org.scalatest.{Matchers, WordSpec}

import scala.util.Try

class AreaCoverageSpec extends WordSpec with Matchers {
  val rectangle = Vector[LatLon](
    LatLon(37.0, -122.0),
    LatLon(37.05, -122.0),
    LatLon(37.05, -122.3),
    LatLon(37.0, -122.3)
  )

  "AreaCoverage" should {
    "construct successfully with a valid polygon" in {
      val coverage = new AreaCoverage(rectangle, 50)
    }

    "fail with an invalid polygon" in {
      val invalidCorners = rectangle.updated(2, rectangle(3)).updated(3, rectangle(2))
      assert(Try(new AreaCoverage(invalidCorners, 50)).isFailure)
    }

    "generate the expected number of waypoints" in {
      val coverage = new AreaCoverage(rectangle, 2665)

      val longPoints = coverage.transects(rectangle(0), 50, true)
      longPoints.size shouldBe 20

      val shortPoints = coverage.transects(rectangle(0), 50)
      shortPoints.size shouldBe 4
    }
  }
}
