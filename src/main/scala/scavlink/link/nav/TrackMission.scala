package scavlink.link.nav

import scavlink.coord.Geo
import scavlink.link.mission._
import scavlink.state.{LocationState, MissionState, State}

import scala.concurrent.duration._

/**
 * Follows the course traveled through the waypoints of a RunMission operation.
 * Computes course status based on distance change with Theil's incomplete method to smooth distance samples.
 * @author Nick Rossi
 */
case class TrackMission(mission: Mission,
                        hasArrived: (Geo, Geo) => Boolean = withinMeters(1, 1),
                        maxEta: FiniteDuration = 6.hours,
                        smoothingWindow: FiniteDuration = 10.seconds,
                        index: Int = 0,
                        current: Option[Geo] = None,
                        isArrived: Boolean = false,
                        isComplete: Boolean = false,
                        distances: Distances = Vector.empty)
  extends MissionCourse with DistanceChangeStatus with TheilIncompleteDistance {
  require(smoothingWindow > 1.second, "smoothingWindow must be at least 1 second")

  val states: Set[Class[_ <: State]] = Set(classOf[LocationState], classOf[MissionState])

  val status: CourseStatus.Value = distanceStatus

  def update(state: State): MissionCourse = state match {
    case s: LocationState =>
      waypoint match {
        case Some(wp) =>
          if (hasArrived(s.location, wp)) {
            this.copy(current = Some(s.location), isArrived = true)
          } else {
            this.copy(current = Some(s.location), isArrived = false,
              distances = addDistance(s.timeIndex, computeDistance(s.location, wp)))
          }

        case None =>
          this.copy(current = Some(s.location), isArrived = false)
      }

    case s: MissionState if s.currentIndex != index =>
      this.copy(index = s.currentIndex, distances = Vector.empty)

    case _ => this
  }

  def completed: MissionCourse = copy(isComplete = true)

  override def fieldsToString() = {
    val fields = super.fieldsToString()
    s"$fields distance=${ distance }m distRate=${ distanceChangeRate * 1000 }m/s eta=$eta"
  }
}
