package scavlink.link.nav

import java.util.concurrent.TimeUnit

import scavlink.link.nav.CourseStatus._

import scala.concurrent.duration._

/**
 * Computes course status by measuring the rate of change in distance to the waypoint and
 * using the value to determine an ETA. If the vehicle isn't moving toward the waypoint 
 * in a timely fashion, we flag the course with an Error status, which causes it to abort.
 *
 * A crude but broadly applicable estimate of whether the autopilot is doing its job.
 * Distance change rate should be negative, and negative enough that the ETA should not
 * be too far in the future. This covers cases where the vehicle is unexpectedly idling in
 * one spot, moving too slowly, or heading in the wrong direction.
 *
 * We compute the rate from a series of recent measurements to smooth out GPS errors and
 * trajectory adjustments by the autopilot.
 *
 * We use ETA as an easy-to-configure threshold. If the distance change rate results in an
 * ETA beyond the acceptable threshold, the course status is computed as "Error".
 * If ETA exceeds 50% of the threshold, a "Warning" is reported.
 *
 * The actual computation of distance change rate is left abstract for alternate methods.
 * [[LeastSquaresDistance]] provides a simple linear regression, while [[TheilIncompleteDistance]]
 * provides a less costly non-parametric regression method. Other approaches may prove better.
 * 
 * @author Nick Rossi
 */
trait DistanceChangeStatus extends Distance {
  /**
   * Determines how many measurements are accumulated to compute the status.
   */
  def smoothingWindow: FiniteDuration

  // if estimated time remaining is longer than this value, consider the course failed
  def maxEta: FiniteDuration

  // (time in ms, distance in m) pairs
  def distances: Distances

  // most recent distance measurement
  lazy val distance: Double = if (distances.isEmpty) Double.NaN else distances.last._2

  // use a 1 second tolerance in lieu of passing "isWindowFull" to the next copy
  private val isWindowFull: Boolean =
    distances.length > 1 && distances.last._1 - distances.head._1 > (smoothingWindow - 1.second).toMillis

  /**
   * Rate of distance change in meters per millisecond (negative number means progress toward waypoint).
   */
  lazy val distanceChangeRate: Double = if (!isWindowFull) Double.NaN else computeChangeRate(distances)

  /**
   * Estimated time to waypoint based on change rate and latest distance.
   */
  lazy val eta: Duration = if (!isWindowFull || distanceChangeRate >= 0) {
    Duration.Inf
  } else {
    val time = (distance / -distanceChangeRate).toLong
    if (time > Long.MaxValue / 1000) {
      Duration.Inf
    } else {
      FiniteDuration(time, TimeUnit.MILLISECONDS)
    }
  }

  /**
   * Determine course status from distance change rate.
   */
  lazy val distanceStatus: CourseStatus.Value = {
    if (!isWindowFull) {
      OK
    } else if (distanceChangeRate > 0 || eta > maxEta) {
      Error
    } else if (eta > maxEta / 2) {
      Warning
    } else {
      OK
    }
  }

  /**
   * Compute the distance change rate.
   * @return distance change rate in m/ms (negative number means progress toward waypoint)
   */
  def computeChangeRate(distances: Distances): Double

  /**
   * Add distance to sample list.
   * @param time time index in milliseconds
   * @param distance distance in meters
   * @return copy of sample list
   */
  def addDistance(time: Long, distance: Double): Distances = {
    require(time >= 0)
    require(distance >= 0)
    val nextEntry = (time, distance)

    // don't duplicate the last entry, which can happen if telemetry didn't change in the interval
    // (it's cheaper to check this than use a SortedMap everywhere)
    if (distances.nonEmpty && distances.last == nextEntry) {
      distances
    } else {
      val cutoff = time - smoothingWindow.toMillis
      distances.dropWhile { case (t, d) => t < cutoff } :+ nextEntry
    }
  }
}
