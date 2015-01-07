package scavlink.link.nav

import scavlink.coord.Geo
import scavlink.state.State

/**
 * Represents a course executed by the RunGuidedCourse actor.
 *
 * An implementation should return an updated course (immutably) with each new
 * telemtry state passed to [[update()]]. Changes in the course's values
 * before and after [[update()]] signal the RunGuidedCourse actor to
 * take action, according to the following contract:
 * <ul>
 * <li> If the [[waypoint]] value changes, RunGuidedCourse will navigate toward the new waypoint.
 * <li> If [[status]] becomes Error, RunGuidedCourse will abort and switch the vehicle to Loiter mode.
 * <li> If [[isComplete]] becomes true, RunGuidedCourse will end successfully and switch to Loiter mode.
 * </ul>
 * This allows for simple, complex or computationally creative courses.
 *
 * @author Nick Rossi
 */
trait GuidedCourse extends Course {
  /**
   * Current waypoint that the vehicle is traveling to.
   */
  def waypoint: Geo

  /**
   * Update the course based on the telemetry provided.
   * @param state telemetry update
   * @return new course data
   */
  def update(state: State): GuidedCourse

  override def fieldsToString() = {
    val fields = super.fieldsToString()
    s"$fields waypoint=$waypoint"
  }
}
