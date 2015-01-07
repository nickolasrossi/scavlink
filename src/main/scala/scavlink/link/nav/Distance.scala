package scavlink.link.nav

import scavlink.coord.Coordinates
import spire.algebra.MetricSpace

trait Distance {
  def distance: Double

  def computeDistance[T <: Coordinates](loc1: T, loc2: T)(implicit ev: MetricSpace[T, Double]): Double =
    ev.distance(loc1, loc2)
}
