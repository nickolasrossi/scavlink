package scavlink.coord

import spire.implicits._
import spire.math._

/**
 * Basic geodetic formulas.
 * @see [[http://www.movable-type.co.uk/scripts/latlong.html]]
 */
object Formulas {
  /**
   * Haversine distance in meters between two latitude/longitude points.
   * Very accurate across all distance lengths.
   */
  def haversineDistance(p1: LatLon, p2: LatLon): Double = {
    val dlat = p2.rlat - p1.rlat
    val dlon = p2.rlon - p1.rlon
    val a = (sin(dlat / 2) ** 2) + p1.rlatCos * p2.rlatCos * (sin(dlon / 2) ** 2)
    EarthRadius * 2 * atan2(sqrt(a), sqrt(1 - a))
  }

  /**
   * Approximate distance between two latitude/longitude points using equirectangular formula.
   * A cheaper computation than haversine at the cost of accuracy at larger distances.
   * Roughly accurate to within 1 meter at a distance of 7km, depending on latitude and direction
   * (greater error toward the poles).
   */
  def equirectangularDistance(p1: LatLon, p2: LatLon): Double = {
    val dlat = p2.lat - p1.lat
    val dlon = (p2.lon - p1.lon) * p2.rlatCos
    sqrt(dlat ** 2 + dlon ** 2).toRadians * EarthRadius
  }

  /**
   * Great-circle midpoint between two latitude/longitude points.
   */
  def midpoint(p1: LatLon, p2: LatLon): LatLon = {
    val Bx = p2.rlatCos * cos(p2.rlon - p1.rlon)
    val By = p2.rlatCos * sin(p2.rlon - p1.rlon)
    val nlat = atan2(p1.rlatSin + p2.rlatSin, sqrt((p1.rlatCos + Bx) ** 2 + By ** 2))
    val nlon = p1.rlon + atan2(By, p1.rlatCos + Bx)
    LatLon(nlat.toDegrees, nlon.toDegrees)
  }

  /**
   * Compute new position a certain distance along a heading from old position.
   * @param p1 old position
   * @param distance distaince in meters
   * @param heading heading in degrees
   * @return new position
   */
  def movePoint(p1: LatLon, distance: Double, heading: Double): LatLon = {
    val ad = distance / EarthRadius
    val adCos = cos(ad)
    val adSin = sin(ad)

    val hd = heading.toRadians
    val hdCos = cos(hd)
    val hdSin = sin(hd)

    val nrlatSin = p1.rlatSin * adCos + p1.rlatCos * adSin * hdCos
    val nrlat = asin(nrlatSin)
    val nlon = p1.lon + atan2(hdSin * adSin * p1.rlatCos, adCos - p1.rlatSin * nrlatSin).toDegrees
    LatLon(nrlat.toDegrees, nlon)
  }

  /**
   * Convert a longitude difference to meters.
   * @param lonDelta longitude difference in degrees
   * @param atLatitude latitude position in degrees
   * @return distance in meters
   */
  def lonDeltaToMeters(lonDelta: Double, atLatitude: Double): Double =
    haversineDistance(LatLon(atLatitude, 0), LatLon(atLatitude, lonDelta))

  /**
   * Convert meters to a longitude difference.
   * @param distance distance in meters
   * @param atLatitude latitude position in degrees
   * @return longitude difference in degrees
   */
  def metersToLonDelta(distance: Double, atLatitude: Double): Double = {
    val k = tan(distance / (2 * EarthRadius))
    val c = cos(atLatitude.toRadians)
    val dlon = 2 * asin(sqrt(1 + k ** 2) * k / c)
    dlon.toDegrees
  }

  def midpoint(p1: Geo, p2: Geo): Geo = Geo(midpoint(p1.latlon, p2.latlon), (p1.alt + p2.alt) / 2)
}
