package scavlink.link.nav

import com.vividsolutions.jts.geom.{Coordinate, LineString}
import scavlink.coord._

/**
 * Computations that produce a series of waypoints to cover a geographic area.
 * @param corners corners of the polygon in order
 * @param spacing distance between coverage lines in meters
 *
 * @author Nick Rossi
 */
class AreaCoverage(val corners: Seq[LatLon], val spacing: Double) {
  val polygon = Shapes.polygon(corners)
  val boundingRectangle = Shapes.orientedBoundingBox(polygon)

  /**
   * Determine waypoints for parallel transect ("lawnmower") coverage of a polygon area.
   * The first waypoint is chosen as the polygon corner closest to the vehicle start point.
   * @param start vehicle start point
   * @param height in meters
   * @param shortTransects whether to use length-wise (false) or width-wise (true) transects
   */
  def transects(start: LatLon, height: Double, shortTransects: Boolean = false): Vector[Geo] = {
    val lines = boxTransects(shortTransects) map { line =>
      val endpoints = polygon.intersection(line).getCoordinates
      (CoordinateToLatLon(endpoints.head), CoordinateToLatLon(endpoints.last))
    }

    val ends = Vector(lines.head._1, lines.head._2, lines.last._1, lines.last._2)
    val (_, closest) = ends.zipWithIndex.minBy { _._1.distance(start) }

    val lineOrder = if (closest >= 2) lines.reverse else lines
    val direction = closest & 1

    Vector.tabulate(lineOrder.length * 2) { i =>
      val j = i / 2
      val k = (i & 1) ^ (j & 1) ^ direction
      val point = if (k > 0) lineOrder(j)._2 else lineOrder(j)._1
      Geo(point, height)
    }
  }

  /**
   * Calculate transect lines from an oriented minimum bounding rectangle.
   */
  private def boxTransects(shortSides: Boolean): Vector[LineString] = {
    val Seq(p1, p2, p3, p4) = boundingRectangle
    val d1 = p1.haversineDistance(p2)
    val d2 = p2.haversineDistance(p3)

    val (start, end, length, width) = if ((d1 > d2) ^ shortSides) {
      ((p1, p2), (p4, p3), d1, d2)
    } else {
      ((p1, p4), (p2, p3), d2, d1)
    }

    val lines = (width / spacing).round.toInt
    val incX = (end._1.x - start._1.x) / lines
    val incY = (end._1.y - start._1.y) / lines

    Vector.tabulate(lines) { i =>
      val inc = i + 0.5
      val x1 = start._1.x + inc * incX
      val y1 = start._1.y + inc * incY
      val x2 = start._2.x + inc * incX
      val y2 = start._2.y + inc * incY
      val a = new Coordinate(x1, y1)
      val b = new Coordinate(x2, y2)
      ctx.getGeometryFactory.createLineString(Array(a, b))
    }
  }
}
