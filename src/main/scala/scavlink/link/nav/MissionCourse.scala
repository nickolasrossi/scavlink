package scavlink.link.nav

import scavlink.coord.Geo
import scavlink.link.mission._
import scavlink.message._
import scavlink.state.State

/**
 * Detailed or computed status of a mission started by the RunMission actor.
 *
 * Unlike GuidedCourse, a MissionCourse does not control a mission, it merely
 * follows one executed by the autopilot.
 *
 * However, MissionCourse may evaluate the progress of a mission and report a
 * Warning or Error in the [[Course.status]] field.
 *
 * If [[Course.status]] becomes Error, the RunMission actor will abort
 * and switch the vehicle to Loiter mode.
 *
 * @author Nick Rossi
 */
trait MissionCourse extends Course {
  def mission: Mission
  def index: Int

  val waypoints: Map[Int, Geo] = mission.map(missionItemCoordinates).zipWithIndex.collect {
    case (Some(location), i) if location.isInstanceOf[Geo] => i -> location.asInstanceOf[Geo]
  }.toMap

  val waypoint: Option[Geo] = waypoints.get(index)

  def update(state: State): MissionCourse

  /**
   * Sets isComplete to true.
   */
  def completed: MissionCourse

  override def fieldsToString() =
    super.fieldsToString() + s" waypoints=$waypoints index=$index waypoint=$waypoint"
}
