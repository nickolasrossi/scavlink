package scavlink.link.nav

import scavlink.coord.Geo
import scavlink.state.State


object CourseStatus extends Enumeration {
  val OK, Warning, Error = Value
}

/**
 * Represents a course traveled by the vehicle.
 *
 * @author Nick Rossi
 */
trait Course {
  /**
   * The current location of the vehicle.
   */
  def current: Option[Geo]
  /**
   * Whether the course is tracking well (Ok), is starting to move off course (Warning),
   * or is fatally off course (Error).
   */
  def status: CourseStatus.Value
  /**
   * Whether the course is completed.
   */
  def isComplete: Boolean
  /**
   * Which telemetry states are required by this course.
   */
  def states: Set[Class[_ <: State]]

  def fieldsToString() = s"current=$current status=$status done=$isComplete"

  final override def toString = s"${ this.getClass.getSimpleName }(${ fieldsToString() }"
}
