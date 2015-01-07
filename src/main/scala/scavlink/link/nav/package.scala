package scavlink.link

import scavlink.coord.Geo
import spire.algebra.{Order, Field}
import spire.implicits._

import scala.language.implicitConversions
import scala.reflect.ClassTag

package object nav {
  type Distances = Vector[(Long, Double)]

  /**
   * Predicate that returns whether the location is within a certain distance of the destination.
   */
  def withinMeters(horizontalRadius: Double, verticalBar: Double)(current: Geo, destination: Geo): Boolean = {
    math.abs(current.alt - destination.alt) < verticalBar &&
      current.latlon.haversineDistance(destination.latlon) < horizontalRadius
  }

  /**
   * Find the median of a sequence of values.
   */
  def median[T](values: Seq[T])(implicit ev: Numeric[T], f: Field[T], ov: Order[T], ct: ClassTag[T]): T = {
    val n = values.length
    val k = n / 2 + 1
    val select = values.qselectk(k)
    val (max, nextMax) = max2(select)
    if (n % 2 > 0) max else (max + nextMax) / 2
  }

  /**
   * Return the top two values from a sequence.
   * @return (max value, next max value)
   */
  def max2[T](values: Seq[T])(implicit ev: Numeric[T], ov: Order[T]): (T, T) = values match {
    case v1 +: v2 +: tail =>
      val first2 = if (v1 > v2) (v1, v2) else (v2, v1)
      tail.foldLeft(first2) { case (top2, v) =>
        if (v > top2._1) (v, top2._1) else if (v > top2._2) (top2._1, v) else top2
      }

    case v +: tail => (v, ev.zero)
    case _ => (ev.zero, ev.zero)
  }
}
