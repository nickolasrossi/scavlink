package scavlink.link.nav

import scavlink.coord._
import scavlink.state.{LocationState, State}

import scala.concurrent.duration._

/**
 * Guided mode course that travels through a sequence of waypoints.
 * Computes distance change with Theil's incomplete method to smooth distance samples.
 * @author Nick Rossi
 */
case class GotoLocations(locations: Seq[Geo],
                         index: Int = 0,
                         hasArrived: (Geo, Geo) => Boolean = withinMeters(5, 1),
                         maxEta: FiniteDuration = 6.hours,
                         smoothingWindow: FiniteDuration = 10.seconds,
                         current: Option[Geo] = None,
                         isComplete: Boolean = false,
                         distances: Distances = Vector.empty)
  extends GuidedCourse with DistanceChangeStatus with TheilIncompleteDistance {
  require(locations.nonEmpty, "Location list must be non-empty")
  require(smoothingWindow > 1.second, "smoothingWindow must be at least 1 second")

  val states: Set[Class[_ <: State]] = Set(classOf[LocationState])

  val waypoint = locations.head

  val status: CourseStatus.Value = distanceStatus

  def update(state: State): GuidedCourse = state match {
    case s: LocationState =>
      if (hasArrived(s.location, waypoint)) {
        if (locations.tail.isEmpty) {
          this.copy(current = Some(s.location), isComplete = true, index = index + 1, distances = Vector.empty)
        } else {
          this.copy(current = Some(s.location), locations = locations.tail, index = index + 1, distances = Vector.empty)
        }
      } else {
        this.copy(current = Some(s.location), distances = addDistance(s.timeIndex, computeDistance(s.location, waypoint)))
      }

    case _ => this
  }

  override def fieldsToString(): String = {
    val fields = super.fieldsToString()
    s"index=$index more=${ locations.tail.nonEmpty } $fields distance=${ distance }m distRate=${ distanceChangeRate * 1000 }m/s eta=$eta"
  }
}
