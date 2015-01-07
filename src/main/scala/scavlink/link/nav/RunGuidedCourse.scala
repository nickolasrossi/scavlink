package scavlink.link.nav

import akka.actor.Status.Failure
import scavlink.coord.Coordinates
import scavlink.link.Vehicle
import scavlink.link.nav.NavTellAPI._
import scavlink.link.operation._
import scavlink.link.telemetry.Telemetry
import scavlink.message.Mode
import scavlink.state.SystemState

/**
 * Move the vehicle along the course implemented by the argument.
 */
case class RunGuidedCourse(course: GuidedCourse) extends NavOp {
  val actorType = classOf[RunGuidedCourseActor]
}

case class RunGuidedCourseResult(vehicle: Vehicle, op: RunGuidedCourse, last: GuidedCourse) extends OpResult
case class RunGuidedCourseFailed(vehicle: Vehicle, op: RunGuidedCourse, last: GuidedCourse,
                                 error: CourseError.Value, message: String) extends OpException

case class RunGuidedCourseStatus(vehicle: Vehicle, op: RunGuidedCourse, last: GuidedCourse, isNewWaypoint: Boolean) extends OpProgress {
  val percent = 0
  override def toString = s"RunGuidedCourseStatus(vehicle=${ vehicle.id } last=$last newWaypoint=$isNewWaypoint op=$op )"
}

/**
 * Instructs the vehicle to follow a specified course in Guided mode.
 * Success response is returned when course.isComplete returns true.
 * Failure response is returned if course.status becomes Error.
 */
class RunGuidedCourseActor(vehicle: Vehicle) extends VehicleOpActor[RunGuidedCourse](vehicle) {
  private case class GuidedData(course: GuidedCourse, receivedSystemState: Boolean) extends OpData

  case object SetGuided extends OpState
  case object SetPoint extends OpState
  case object Teardown extends OpState

  def guidedPoint(location: Coordinates): Conversation = Conversation(NavOps.guidedPoint(vehicle, location))


  // FSM states

  when(Idle) {
    case Event(op: RunGuidedCourse, Uninitialized) =>
      start(op, sender())
      vehicle.setMode(Mode.Guided)
      goto(SetGuided) using GuidedData(op.course, false)
  }

  when(SetGuided) {
    case Event(_: ConversationSucceeded, GuidedData(course, _)) =>
      link.events.subscribe(self, Telemetry.subscribeTo(vehicle, course.states + classOf[SystemState]))
      vehicle.holdConversation(guidedPoint(course.waypoint))
      goto(SetPoint)

    case Event(Failure(r: ConversationFailed), GuidedData(course, _)) =>
      stop using Finish(RunGuidedCourseFailed(vehicle, op, course, CourseError.SetupFailed,
        s"Failed to set Guided mode: ${ r.reply }"))

    case Event(StateTimeout, GuidedData(course, _)) =>
      stop using Finish(RunGuidedCourseFailed(vehicle, op, course, CourseError.SetupFailed,
        "Timeout on set Guided mode"))
  }

  when(SetPoint) {
    case Event(_: ConversationSucceeded, _) =>
      goto(Active)

    case Event(Failure(r: ConversationFailed), GuidedData(course, _)) =>
      stop using Finish(RunGuidedCourseFailed(vehicle, op, course, CourseError.SetupFailed,
        s"Failed to set destination: ${ r.reply }"))

    case Event(StateTimeout, GuidedData(course, _)) =>
      stop using Finish(RunGuidedCourseFailed(vehicle, op, course, CourseError.SetupFailed,
        "Timeout on set destination"))
  }

  onTransition {
    case SetPoint -> Active =>
      nextStateData match {
        case GuidedData(course, _) => origin ! RunGuidedCourseStatus(vehicle, op, course, true)
        case _ => //
      }
  }

  when(Active, stateTimeout = operationSettings.telemetryTimeout) {
    case Event(Telemetry(_, state, _), GuidedData(course, receivedSystemState)) =>
      state match {
        // only check for unexpected mode change when this isn't the first SystemState message,
        // since the first one might have come before the initial mode change
        case s: SystemState if receivedSystemState && s.specificMode != Mode.Guided(vehicleType) =>
          val mode = Mode.from(vehicleType, s.specificMode).map(_.name).getOrElse(s.specificMode.toString)
          stop using Finish(RunGuidedCourseFailed(vehicle, op, course, CourseError.UnexpectedModeChange,
            s"Unexpected mode change from Guided to $mode"))

        case s =>
          val newCourse = course.update(state)
          val newReceived = receivedSystemState || state.isInstanceOf[SystemState]
          log.debug(s.toString)

          if (newCourse eq course) {
            if (receivedSystemState == newReceived) {
              stay()
            } else {
              stay using GuidedData(course, newReceived)
            }
          } else {
            if (newCourse.current != course.current && newCourse.waypoint == course.waypoint) {
              origin ! RunGuidedCourseStatus(vehicle, op, newCourse, false)
            }

            newCourse match {
              case nc if nc.isComplete =>
                log.debug(s"Course completed")
                goto(Teardown) using Finish(RunGuidedCourseResult(vehicle, op, nc))

              case nc if nc.status == CourseStatus.Error =>
                log.debug(s"Course error detected in $nc")
                goto(Teardown) using Finish(RunGuidedCourseFailed(vehicle, op, nc, CourseError.OffCourse,
                  "Vehicle is off course"))

              case nc if nc.waypoint != course.waypoint =>
                log.debug(s"Next waypoint: ${nc.waypoint}")
                vehicle.holdConversation(guidedPoint(nc.waypoint))
                goto(SetPoint) using GuidedData(nc, newReceived)

              case nc =>
                stay using GuidedData(nc, newReceived)
            }
          }
      }

    case Event(StateTimeout, GuidedData(course, _)) =>
      goto(Teardown) using Finish(RunGuidedCourseFailed(vehicle, op, course, CourseError.TelemetryTimeout,
        s"Telemetry not received in ${ context.receiveTimeout }"))
  }

  onTransition {
    case _ -> Teardown =>
      link.events.unsubscribe(self)
      vehicle.setMode(Mode.Loiter)
  }

  when(Teardown) {
    case Event(_: ConversationSucceeded, finish: Finish) =>
      stop using finish

    case Event(Failure(f: ConversationFailed), finish: Finish) =>
      val error = s"Failed to set Loiter mode: ${ f.reply }"
      val newFinish = finish match {
        case Finish(fin: RunGuidedCourseResult) =>
          RunGuidedCourseFailed(vehicle, fin.op, fin.last, CourseError.TeardownFailed, error)

        case Finish(fin: RunGuidedCourseFailed) =>
          fin.copy(message = fin.message + " | " + error)
      }

      stop using Finish(newFinish)
  }

  /**
   * If actor is stopping unexpectedly and we were in the middle of a course,
   * set the vehicle to Loiter mode.
   */
  override def unexpectedStop(state: OpState, data: OpData): Unit = state match {
    case SetPoint | Active => vehicle.setMode(Mode.Loiter)
    case _ =>
  }
}
