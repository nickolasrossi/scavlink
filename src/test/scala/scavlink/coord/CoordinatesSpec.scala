package scavlink.coord

import org.scalatest.{Matchers, WordSpec}
import spire.implicits._

import scala.util.Try

class CoordinatesSpec extends WordSpec with Matchers {
  val p1 = LatLon(10.325, -51.924)
  val p2 = LatLon(28.300, 2.114)
  val sp1 = Geo(p1, 10)
  val sp2 = Geo(p2, 40)

  val close1 = LatLon(10.325552, -51.924442)
  val close2 = LatLon(10.325798, -51.924398)
  val closeGeo1 = Geo(close1, 10)
  val closeGeo2 = Geo(close2, 40)

  "the LatLon class" should {
    "not allow latitudes beyond 90 N or S" in {
      assert(Try(LatLon(100, 0)).isFailure)
      assert(Try(LatLon(-100, 0)).isFailure)
    }

    "not allow longitudes beyond 180 E or W" in {
      assert(Try(LatLon(0, 181)).isFailure)
      assert(Try(LatLon(0, -181)).isFailure)
    }

    "fix lat/lon to a maximum 7 decimal digits" in {
      LatLon(10.32555199999, -51.924441999999) shouldBe close1
    }

    "compute the simple distance between two nearby points" in {
      close1.equirectangularDistance(close2) shouldBe 27.805165437099514
    }

    "compute the haversine distance between two points" in {
      p1.haversineDistance(p2) shouldBe 5964796.49738564
    }

    "compute the plain distance as the haversine distance" in {
      p1.haversineDistance(p2) shouldBe p1.haversineDistance(p2)
    }

    "allow addition" in {
      p1 + p2 shouldBe LatLon(38.625, -49.81)
    }

    "allow subtraction" in {
      close1 - close2 shouldBe (27.384435875347425,4.818702810244506)
    }

    "allow scalar multiplication" in {
      2 *: p1 shouldBe LatLon(20.65, -103.848)
    }

    "allow reference to x, y, z" in {
      p1._x shouldBe 10.325
      p1._y shouldBe -51.924
      p1._z shouldBe 0
    }

    "add meters consistent with simple distance" in {
      // latitude direction
      val lat1 = close1.copy(lat = close2.lat)
      val dlat = close1.equirectangularDistance(lat1)
      val nlat = close1.+(dlat, 0)
      nlat shouldBe lat1

      // longitude direction
      val lon1 = close1.copy(lon = close2.lon)
      val dlon = close1.equirectangularDistance(lon1)
      val nlon = close1.+(0, dlon)
      nlon shouldBe lon1
    }

    "add distance and heading" in {
      close1.move(100, 66) shouldBe LatLon(10.3259174, -51.9236078)
    }
  }

  "the Geo class" should {
    "construct from three arguments" in {
      Geo(10.325, -51.924, 10) shouldBe sp1
    }

    "allow negative altitude" in {
      assert(Try(Geo(p1, -10)).isSuccess)
    }

    "fix lat/lon to 7 decimal digits, alt to 4 digits" in {
      Geo(10.3249999999, -51.923999999, 10.000000001) shouldBe sp1
    }

    "compute simple distance that includes the altitude" in {
      closeGeo1.equirectangularDistance(closeGeo2) shouldBe 40.90387831626803
    }

    "compute plain distance equal to haversine distance" in {
      closeGeo1.distance(closeGeo2) shouldBe closeGeo1.haversineDistance(closeGeo2)
    }

    "allow addition" in {
      sp1 + sp2 shouldBe Geo(LatLon(38.625, -49.81), 50)
    }

    "allow subtraction" in {
      closeGeo1 - closeGeo2 shouldBe NED(27.384435875347425, 4.818702810244506, 30.0)
    }

    "allow scalar multiplication" in {
      2 *: sp1 shouldBe Geo(LatLon(20.65, -103.848), 20)
    }

    "allow reference to x, y, z" in {
      sp1.x shouldBe 10.325
      sp1.y shouldBe -51.924
      sp1.z shouldBe 10
    }

    "allow addition of local NED coordinates" in {
      closeGeo1 + NED(1, 2, 3) shouldBe Geo(10.325561, -51.9244237, 7)
    }

    "allow addition of local ENU coordinates" in {
      closeGeo1 + ENU(1, 2, 3) shouldBe Geo(10.32557, -51.9244329, 13)
    }
  }

  "Formulas" should {
    "convert meters to degrees at latitude" in {
      Formulas.lonDeltaToMeters(.1, 45).round shouldBe 7871
      Formulas.lonDeltaToMeters(.1, 60).round shouldBe 5566
      Formulas.lonDeltaToMeters(.1, 23.5).round shouldBe 10209
    }

    "convert degrees to meters at latitude" in {
      (Formulas.metersToLonDelta(7871, 45) * 10).round shouldBe 1
      (Formulas.metersToLonDelta(5566, 60) * 10).round shouldBe 1
      (Formulas.metersToLonDelta(10209, 23.5) * 10).round shouldBe 1
    }
  }
}
