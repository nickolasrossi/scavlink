package scavlink.link.fence

import scavlink.coord._
import com.spatial4j.core.context.jts.JtsSpatialContext
import com.spatial4j.core.shape.impl.{GeoCircle, PointImpl}
import com.spatial4j.core.shape.jts.{JtsGeometry, JtsPoint}
import com.spatial4j.core.shape.{Shape, SpatialRelation}
import com.vividsolutions.jts.geom.Coordinate
import spire.implicits._
import spire.math.Interval

sealed trait Fence {
  def contains(point: LatLon): Boolean
  def contains(point: Geo): Boolean
  def overlaps(that: Fence): Boolean
  def |(that: Fence): Fence = Fence.union(this, that)
}

/**
 * A 3D fence.
 * The horizontal dimensions are described by a closed shape in lat/lon coordinates.
 * The vertical dimension is defined by a fixed altitude range.
 * @param shape a closed geometric shape in latitude/longitude coordinates
 * @param altitude an altitude range in meters
 */
case class ShapeFence(shape: Shape, altitude: Interval[Double]) extends Fence {
  require(shape.hasArea)

  /**
   * Whether the latitude/longitude point is within the fence.
   * Altitude is not checked.
   */
  def contains(point: LatLon): Boolean = {
    val jtsPoint = new JtsPoint(Fence.ctx.getGeometryFactory.createPoint(Fence.toCoordinate(point)), Fence.ctx)
    jtsPoint.relate(shape) == SpatialRelation.WITHIN
  }

  /**
   * Whether the geodetic point is within the fence.
   * Altitude is checked.
   */
  def contains(point: Geo): Boolean = altitude.contains(point.alt) && this.contains(point.latlon)

  /**
   * Whether this fence overlaps with another.
   */
  def overlaps(that: Fence): Boolean = that match {
    case r: ShapeFence => this.altitude.intersects(r.altitude) && this.shape.relate(r.shape) != SpatialRelation.DISJOINT
    case r: WorldFence => this.altitude.intersects(r.altitude)
    case r: FenceUnion => r.overlaps(this)
  }

  override def toString = {
    val shapeStr = Fence.ctx.toString(shape)
    s"Shape($shapeStr, alt=$altitude)"
  }
}

/**
 * Region that represents the world but with an altitude restriction.
 * @param altitude altitude range
 */
case class WorldFence(altitude: Interval[Double]) extends Fence {
  def contains(point: LatLon): Boolean = true
  def contains(point: Geo): Boolean = altitude.contains(point.alt)
  def overlaps(that: Fence): Boolean = that match {
    case r: WorldFence => this.altitude.intersects(r.altitude)
    case r => r.overlaps(this)
  }
  override def toString = s"World(alt=$altitude)"
}

case class FenceUnion(left: Fence, right: Fence) extends Fence {
  def contains(point: LatLon): Boolean = left.contains(point) || right.contains(point)
  def contains(point: Geo): Boolean = left.contains(point) || right.contains(point)
  def overlaps(that: Fence): Boolean = left.overlaps(that) || right.overlaps(that)
  override def toString = s"Union($left | $right)"
}


object Fence {
  val ctx = JtsSpatialContext.GEO
  def toCoordinate(p: LatLon) = new Coordinate(p.lon, p.lat)

  /**
   * Create a polygon fence from the specified corners.
   */
  def polygon(corners: Seq[LatLon], altitude: Interval[Double] = Interval.all[Double]): ShapeFence = {
    val shape = new JtsGeometry(Shapes.polygon(corners), ctx, true, true)
    ShapeFence(shape, altitude)
  }

  /**
   * Create a circular ("tin can") fence.
   * @param center circle center coordinates
   * @param radius radius in meters
   * @param altitude altitude range
   */
  def circle(center: LatLon, radius: Double, altitude: Interval[Double] = Interval.all[Double]): ShapeFence = {
    val shape = new GeoCircle(new PointImpl(center.lon, center.lat, ctx),
      Formulas.metersToLonDelta(radius, center.lat), ctx)
    ShapeFence(shape, altitude)
  }

  /**
   * Create a fence from a Well-Known Text markup string.
   * @param text WKT markup
   * @param altitude altitude range
   */
  def wkt(text: String, altitude: Interval[Double] = Interval.all[Double]): ShapeFence =
    ShapeFence(ctx.readShapeFromWkt(text), altitude)

  /**
   * Create a fence that covers the whole world with an altitude range.
   * @param altitude altitude range
   */
  def world(altitude: Interval[Double]): WorldFence = WorldFence(altitude)

  /**
   * Whole world with no altitude restriction.
   */
  val world: WorldFence = world(Interval.all[Double])

  /**
   * Combine two fences.
   */
  def union(left: Fence, right: Fence): Fence = (left, right) match {
    case (l, `left`)  => l
    case (`world`, _) => world
    case (_, `world`) => world
    case (w@WorldFence(altw), ShapeFence(_, alts)) if altw.isSupersetOf(alts) => w
    case (ShapeFence(_, alts), w@WorldFence(altw)) if altw.isSupersetOf(alts) => w
    case (WorldFence(alt1), WorldFence(alt2)) if alt1.intersects(alt2) =>
      WorldFence(alt1 | alt2)
    case (ShapeFence(s1, alt1), ShapeFence(s2, alt2)) if alt1.intersects(alt2) && s1 == s2 =>
      ShapeFence(s1, alt1 | alt2)
    case (l, r) => FenceUnion(l, r)
  }
}
