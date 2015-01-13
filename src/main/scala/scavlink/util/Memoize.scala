package scavlink.util

import scala.collection.mutable

/**
 * General memoize wrapper for a function.
 * From [[http://stackoverflow.com/questions/16257378/is-there-a-generic-way-to-memoize-in-scala]]
 * Not robust for recursive functions, but we aren't using it for that purpose.
 */
case class Memoize[A, B](f: A => B) extends (A => B) {
  private[this] val cache = mutable.Map.empty[A, B]
  def apply(x: A): B = cache.getOrElseUpdate(x, f(x))
}
