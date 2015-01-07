package scavlink.link.fence

import scavlink.coord.LatLon
import spire.implicits._
import spire.math.Interval

trait FenceTestData {
  // rectangle
  val bounds1 = Vector(
    LatLon(47.0, -122.2),
    LatLon(47.1, -122.2),
    LatLon(47.1, -122.3),
    LatLon(47.0, -122.3)
  )

  val fence1 = Fence.polygon(bounds1)
  val fenceMin80 = Fence.polygon(bounds1, Interval.above(80.0))
  val fenceMax80 = Fence.polygon(bounds1, Interval.atOrBelow(80.0))
  val fenceMin60Max80 = Fence.polygon(bounds1, Interval.closed(60.0, 80.0))
  val fenceDepth40 = Fence.polygon(bounds1, Interval.atOrAbove(-40D))
  val worldMax80 = Fence.world(fenceMax80.altitude)

  val pointInside1 = LatLon(47.05, -122.25)
  val pointOutside = LatLon(1, 1)

  val config1 = "corners = [ 47.0,-122.2, 47.1,-122.2, 47.1,-122.3, 47.0,-122.3 ]"
  val configMax80 = config1 + "\nupper-altitude = 80"
  val configMin60Max80 = config1 + "\naltitude = \"[60, 80]\""

  // rectangle
  val fence2 = Fence.polygon(Vector(
    LatLon(48.2, -123.1),
    LatLon(48.3, -123.1),
    LatLon(48.3, -123.2),
    LatLon(48.2, -123.2)
  ))

  val pointInside2 = LatLon(48.22, -123.13)

  val config12 = "wkt = \"MULTIPOLYGON (((-122.2 47.0, -122.2 47.1, -122.3 47.1, -122.3 47.0, -122.2 47.0)), ((-123.1 48.2, -123.1 48.3, -123.2 48.3, -123.2 48.2, -123.1 48.2)))\""

  // triangle
  val fence3 = Fence.polygon(Vector(
    LatLon(48.1, -123.0),
    LatLon(48.4, -123.0),
    LatLon(48.1, -123.3)
  ))

  val pointInside3 = LatLon(48.11, -123.01)
  val pointOutside3 = LatLon(48.3, -123.2)

  // square
  val fence3Overlap = Fence.polygon(Vector(
    LatLon(48.4, -123.0),
    LatLon(48.52, -123.0),
    LatLon(48.52, -123.12),
    LatLon(48.4, -123.12)
  ))

  // circles
  val fenceCircle4 = Fence.circle(LatLon(47.05, -122.25), 8000)
  val pointInside4 = LatLon(47.11, -122.25)
  val pointOutside4 = LatLon(47.15, -122.35)

  val config4 = "circle = { lat = 47.05, lon = -122.25, radius = 8000 }"

  val fenceCircle5 = Fence.circle(LatLon(48.1, -123.1), 10000)
  val fenceCircle6 = Fence.circle(LatLon(47.8, -123.1), 13000)

  val pointInside7 = LatLon(47.8, -123.1)
  val fenceCircle7 = Fence.circle(pointInside7, 100)

  val datelineBounds = Vector(
    LatLon(47.1, 179.9),
    LatLon(47.1, -179.9),
    LatLon(47.0, -179.9),
    LatLon(47.0, 179.9)
  )

  val datelineFence = Fence.polygon(datelineBounds)
  val insideWest = LatLon(47.05, -179.99990)
  val insideEast = LatLon(47.05, 179.99990)
}
