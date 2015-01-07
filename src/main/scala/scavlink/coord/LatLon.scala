package scavlink.coord

import spire.implicits._
import spire.math._

/**
 * Geospatial location.
 * Values are truncated to 7 decimal digits (11mm accuracy).
 * @param lat latitude in degrees
 * @param lon longitude in degrees
 * @author Nick Rossi
 */
class LatLon private(val lat: Double, val lon: Double) {
  require(lat >= -90 && lat <= 90, s"latitude must be between -90 and 90 (was $lat)")
  require(lon >= -180 && lon <= 180, s"longitude must be between -180 and 180 (was $lon)")

  // keep computed values for reuse
  lazy val rlat = lat.toRadians
  lazy val rlon = lon.toRadians
  lazy val rlatSin = sin(rlat)
  lazy val rlatCos = cos(rlat)

  /**
   * @see [[Formulas.equirectangularDistance()]]
   * @return distance in meters
   */
  def equirectangularDistance(that: LatLon): Double = Formulas.equirectangularDistance(this, that)

  /**
   * @see [[Formulas.haversineDistance()]]
   * @return distance in meters
   */
  def haversineDistance(that: LatLon): Double = Formulas.haversineDistance(this, that)

  /**
   * @see [[Formulas.midpoint()]]
   */
  def midpoint(that: LatLon): LatLon = Formulas.midpoint(this, that)

  /**
   * @see [[Formulas.movePoint()]]
   */
  def move(distance: Double, heading: Double): LatLon = Formulas.movePoint(this, distance, heading)

  /**
   * Add vector in meters to latitude and longitude using equirectangular distance.
   * @param north meters in latitude direction
   * @param east meters in longitude direction
   * @return new LatLon
   */
  def +(north: Double, east: Double): LatLon = {
    val nlat = lat + (north / EarthRadius).toDegrees
    val nlon = lon + (east / (EarthRadius * rlatCos)).toDegrees
    LatLon(nlat, nlon)
  }

  /**
   * Vector difference between two LatLon points in meters using equirectangular distance.
   * @return (north, east) difference
   */
  def -(that: LatLon): (Double, Double) = {
    val dlat = that.lat - this.lat
    val dlon = (that.lon - this.lon) * that.rlatCos
    (dlat.toRadians * EarthRadius, dlon.toRadians * EarthRadius)
  }

  def copy(lat: Double = lat, lon: Double = lon): LatLon = LatLon(lat, lon)

  def toVector = Vector(lat, lon)

  override def toString = s"($lat, $lon)"

  override def equals(obj: Any): Boolean = obj match {
    case that: LatLon => lat == that.lat && lon == that.lon
    case _ => false
  }
}

object LatLon {
  def apply(lat: Double = 0, lon: Double = 0): LatLon = new LatLon(roundLatLon(lat), roundLatLon(lon))
  def apply(tuple: (Double, Double)): LatLon = apply(tuple._1, tuple._2)

  def apply(arr: Iterable[Double]): LatLon = {
    val iter = arr.iterator
    def next(): Double = if (iter.hasNext) iter.next() else 0d
    apply(next(), next())
  }

  def scaledInt(lat: Int = 0, lon: Int = 0): LatLon = new LatLon(lat / LatLonScale, lon / LatLonScale)

  def unapply(p: LatLon): Option[(Double, Double)] = Some((p.lat, p.lon))
}