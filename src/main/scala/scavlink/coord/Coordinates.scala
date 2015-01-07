package scavlink.coord

import spire.implicits._
import spire.math._

/**
 * Seal up the coordinate classes that are allowed in MAVLink commands.
 */
sealed trait Coordinates extends XYZ[Double]

/**
 * Geodetic coordinates (latitude/longitude/altitude).
 * Latitude/longitude are truncated to 7 decimal digits (11mm accuracy).
 * Altitude is truncated to 4 decimal digits (.1mm).
 * @param latlon geospatial location
 * @param alt altitude in meters
 * @author Nick Rossi
 */
class Geo private(val latlon: LatLon, val alt: Double) extends Coordinates {
  val lat = latlon.lat
  val lon = latlon.lon

  val x = lat
  val y = lon
  val z = alt

  /**
   * @see [[Formulas.equirectangularDistance())]]
   * @return distance in meters
   */
  def equirectangularDistance(that: Geo): Double =
    computeDistance(that, that.latlon.equirectangularDistance(this.latlon))

  /**
   * @see [[Formulas.haversineDistance()]]
   * @return distance in meters
   */
  def haversineDistance(that: Geo): Double = computeDistance(that, that.latlon.haversineDistance(this.latlon))

  private def computeDistance(that: Geo, latlonDistance: Double): Double = {
    val dalt = that.alt - this.alt
    sqrt(latlonDistance ** 2 + dalt ** 2)
  }

  /**
   * Add NED coordinates using equirectangular distance.
   */
  def +(ned: NED): Geo = Geo(latlon +(ned.north, ned.east), alt - ned.down)

  /**
   * Add ENU coordinates using equirectangular distance.
   */
  def +(enu: ENU): Geo = Geo(latlon +(enu.north, enu.east), alt + enu.up)

  /**
   * NED difference in meters using equirectangular distance.
   * @return (north, east, down) difference
   */
  def -(that: Geo): NED = {
    val (dx, dy) = this.latlon - that.latlon
    NED(dx, dy, that.alt - this.alt)
  }

  /**
   * Return a new coordinate along the distance and heading from this coordinate.
   * Accurate over short and long distances.
   */
  def move(distance: Double, heading: Double, altChange: Double = 0): Geo =
    Geo(latlon.move(distance, heading), alt + altChange)

  override def toString = s"($latlon, ${alt}m)"

  override def equals(obj: Any): Boolean = obj match {
    case that: Geo => latlon == that.latlon && alt == that.alt
    case _ => false
  }
}

object Geo {
  def apply(latlon: LatLon = LatLon(), alt: Double = 0): Geo = new Geo(latlon, roundAlt(alt))
  def apply(lat: Double, lon: Double, alt: Double): Geo = apply(LatLon(lat, lon), alt)
  def apply(tuple: (Double, Double, Double)): Geo = apply(tuple._1, tuple._2, tuple._3)

  def apply(arr: Iterable[Double]): Geo = {
    val iter = arr.iterator
    def next(): Double = if (iter.hasNext) iter.next() else 0d
    apply(next(), next(), next())
  }

  def scaledInt(lat: Int, lon: Int, alt: Int): Geo = apply(LatLon.scaledInt(lat, lon), alt / AltScale)
  def unapply(p: Geo): Option[(LatLon, Double)] = Some((p.latlon, p.alt))
}


/**
 * The NED coordinate frame in meters.
 */
case class NED(north: Double = 0, east: Double = 0, down: Double = 0) extends Coordinates {
  val x = north
  val y = east
  val z = down
  def enu = ENU(east, north, -down)
}

/**
 * The ENU coordinate frame in meters.
 */
case class ENU(east: Double = 0, north: Double = 0, up: Double = 0) extends Coordinates {
  val x = east
  val y = north
  val z = up
  def ned = NED(north, east, -up)
}
