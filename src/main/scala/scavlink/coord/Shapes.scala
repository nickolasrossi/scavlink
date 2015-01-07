package scavlink.coord

import com.spatial4j.core.context.jts.JtsSpatialContext
import com.spatial4j.core.shape.jts.JtsPoint
import com.vividsolutions.jts.geom.{Coordinate, Polygon}

/**
 * Convenience constructors for shapes in the spatial4j library.
 */
object Shapes {
  val ctx = JtsSpatialContext.GEO

  def point(p: LatLon) = new JtsPoint(ctx.getGeometryFactory.createPoint(p), ctx)

  def polygon(corners: Seq[LatLon]): Polygon = {
    val ring = if (corners.last == corners.head) corners else corners :+ corners.head
    val polygon = ctx.getGeometryFactory.createPolygon(ring.map(LatLonToCoordinate).toArray)
    require(polygon.isValid, s"Not a valid polygon: $ring")
    polygon
  }

  /**
   * Returns the minimum rectange that encloses the specified corners,
   * which may be at an angle to the perpendicular.
   * @return rectangle corners
   */
  def orientedBoundingBox(corners: Seq[LatLon]): Seq[LatLon] = {
    val poly = polygon(corners)
    val box = orientedBoundingBox(poly)
    box.map(CoordinateToLatLon)
  }

  /**
   * From [[https://gist.github.com/tamland/8953637]]
   */
  def orientedBoundingBox(polygon: Polygon): Seq[Coordinate] = {
    def rotate(p: Coordinate, angle: Double): Coordinate = {
      val cs = math.cos(angle)
      val sn = math.sin(angle)
      val x = p.x * cs - p.y * sn
      val y = p.x * sn + p.y * cs
      new Coordinate(x, y)
    }

    val hull = polygon.convexHull().getCoordinates

    val (area, rect) = (1 until hull.size).map { i =>
      val (u, v) = (hull(i-1), hull(i))
      val angle = math.atan((u.x - v.x) / (u.y - v.y))

      val rotated = hull.map(rotate(_, angle))
      val xs = rotated.map(_.x)
      val ys = rotated.map(_.y)
      val (x1, y1) = (xs.min, ys.min)
      val (x2, y2) = (xs.max, ys.max)

      val area = (x2 - x1) * (y2 - y1)
      val p1 = rotate(new Coordinate(x1, y1), -angle)
      val p2 = rotate(new Coordinate(x1, y2), -angle)
      val p3 = rotate(new Coordinate(x2, y2), -angle)
      val p4 = rotate(new Coordinate(x2, y1), -angle)
      (area, Seq(p1, p2, p3, p4))
    }.minBy(_._1)

    rect
  }
}
