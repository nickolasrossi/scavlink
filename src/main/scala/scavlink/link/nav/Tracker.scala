package scavlink.link.nav

import scavlink.coord.Geo

/**
 * Accumulates reported locations during a Course.
 * Since this may consume an unbounded amount of memory, it's not to be used lightly,
 * but here's a little rope.
 * @author Nick Rossi
 */
trait Tracker {
  def track: Vector[(Long, Geo)]
  def addLocation(time: Long, location: Geo): Vector[(Long, Geo)]
}

trait WholeTracker extends Tracker {
  def addLocation(time: Long, location: Geo): Vector[(Long, Geo)] = track :+ (time, location)
}

trait RecentTracker extends Tracker {
  def maxLocations: Int
  def addLocation(time: Long, location: Geo): Vector[(Long, Geo)] = track.takeRight(maxLocations - 1) :+ (time, location)
}
