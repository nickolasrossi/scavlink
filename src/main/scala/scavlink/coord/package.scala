package scavlink

import com.spatial4j.core.context.jts.JtsSpatialContext
import com.vividsolutions.jts.geom.Coordinate
import spire.algebra.{CoordinateSpace, Field, MetricSpace}
import spire.implicits._

import scala.language.implicitConversions

package object coord {
  val ctx = JtsSpatialContext.GEO

  val EarthRadius: Double = 6378100

  // truncation/scale factors for geodetic values
  val LatLonScale = 1e7
  val AltScale = 1e4

  def roundLatLon = round(LatLonScale) _
  def roundAlt = round(AltScale) _
  def round(scale: Double)(x: Double): Double = (x * scale).round.toDouble / scale

  // convert between LatLon and spatial4j
  implicit def CoordinateToLatLon(p: Coordinate): LatLon = LatLon(p.y, p.x)
  implicit def LatLonToCoordinate(p: LatLon): Coordinate = new Coordinate(p.lon, p.lat)


  /**
   * Spire implicit for LatLon
   */
  implicit object LatLonSpace extends CoordinateSpace[LatLon, Double] with MetricSpace[LatLon, Double] {
    implicit def scalar: Field[Double] = implicitly[Field[Double]]

    def dimensions: Int = 2

    def coord(v: LatLon, i: Int): Double = i match {
      case 0 => v.lat
      case 1 => v.lon
      case _ => 0
    }

    def axis(i: Int): LatLon = i match {
      case 0 => LatLon(1, 0)
      case 1 => LatLon(0, 1)
      case _ => zero
    }

    def zero: LatLon = LatLon(0, 0)
    def timesl(r: Double, v: LatLon): LatLon = LatLon(r * v.lat, r * v.lon)
    def negate(x: LatLon): LatLon = LatLon(-x.lat, -x.lon)
    def plus(x: LatLon, y: LatLon): LatLon = LatLon(x.lat + y.lat, x.lon + y.lon)
    def distance(v: LatLon, w: LatLon): Double = v.haversineDistance(w)
  }


  /**
   * Spire implicit for Geo
   */
  implicit object GeoSpace extends XYZSpace[Geo](Geo.apply) {
    override def distance(v: Geo, w: Geo): Double = v.haversineDistance(w)
  }

  implicit object NEDCoordinateSpace extends XYZSpace[NED](NED.apply)

  implicit object ENUCoordinateSpace extends XYZSpace[ENU](ENU.apply)

  implicit object Vector3CoordinateSpace extends XYZSpace[Vector3](Vector3.apply)


  abstract class XYZSpace[T <: XYZ[Double]](build: (Double, Double, Double) => T) extends CoordinateSpace[T, Double] with MetricSpace[T, Double] {
    implicit def scalar: Field[Double] = implicitly[Field[Double]]

    def dimensions: Int = 3

    def axis(i: Int): T = i match {
      case 0 => build(1, 0, 0)
      case 1 => build(0, 1, 0)
      case 2 => build(0, 0, 1)
      case _ => zero
    }

    def coord(v: T, i: Int): Double = i match {
      case 0 => v.x
      case 1 => v.y
      case 2 => v.z
      case _ => 0
    }

    def zero = build(0, 0, 0)

    def timesl(r: Double, v: T) = build(r * v.x, r * v.y, r * v.z)
    def negate(v: T) = build(-v.x, -v.y, -v.z)
    def plus(v: T, w: T) = build(v.x + w.x, v.y + w.y, v.z + w.z)

    def distance(v: T, w: T): Double =
      math.sqrt((w.x - v.x) ** 2 + (w.y - v.y) ** 2 + (w.z - v.z) ** 2)

    def distance(vs: Seq[T]): Double = if (vs.isEmpty) 0D else {
      vs.zip(vs.tail).foldLeft(0D) { case (d, (v1, v2)) => d + distance(v1, v2) }
    }
  }
}
