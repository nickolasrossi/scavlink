package scavlink.link.nav

import akka.actor.Props
import akka.actor.Status.Failure
import scavlink.link.Vehicle
import scavlink.link.mission._
import scavlink.link.nav.NavTellAPI._
import scavlink.link.operation._
import scavlink.link.telemetry.Telemetry
import scavlink.message.Mode
import scavlink.message.common.MissionStart
import scavlink.message.enums.MavCmd
import scavlink.state._

case class RunMission(mission: Mission, course: MissionCourse) extends NavOp {
  val actorType = classOf[RunMissionActor]
}

case class RunMissionResult(vehicle: Vehicle, op: RunMission, last: MissionCourse) extends OpResult

case class RunMissionFailed(vehicle: Vehicle, op: RunMission, last: MissionCourse,
                            error: CourseError.Value, message: String) extends OpException

case class RunMissionStatus(vehicle: Vehicle, op: RunMission, last: MissionCourse, isNewWaypoint: Boolean) extends OpProgress {
  val percent = 0
  override def toString = {
    val cmds = op.mission.map { c => MavCmd(c.command) }
    s"RunMissionStatus(vehicle=${ vehicle.id } commands=$cmds last=$last isNewWaypoint=$isNewWaypoint)"
  }
}


object RunMissionActor {
  def props(vehicle: Vehicle) = Props(classOf[RunMissionActor], vehicle)
}

/**
 * Executes a mission and tracks its progress.
 * @author Nick Rossi
 */
class RunMissionActor(vehicle: Vehicle) extends VehicleOpActor[RunMission](vehicle) {
  private case class MissionData(course: MissionCourse, receivedSystemState: Boolean) extends OpData

  private case object WriteMission extends OpState
  private case object StartMission extends OpState
  private case object AbortMission extends OpState


  when(Idle) {
    case Event(op: RunMission, Uninitialized) =>
      start(op, sender())
      vehicle.missionCache ! SetMission(op.mission)
      goto(WriteMission) using MissionData(op.course, false)
  }

  when(WriteMission) {
    case Event(SetMissionResult(`vehicle`, _, mission), _) =>
      vehicle.holdConversation(Conversation(ConversationStep(MissionStart())))
      goto(StartMission)

    case Event(Failure(SetMissionFailed(`vehicle`, _, result)), MissionData(course, _)) =>
      stop using Finish(RunMissionFailed(vehicle, op, course, CourseError.SetupFailed,
        s"SetMission failed with result $result"))
  }

  when(StartMission) {
    case Event(_: ConversationSucceeded, MissionData(course, _)) =>
      link.events.subscribe(self, Telemetry.subscribeTo(vehicle, course.states + classOf[SystemState]))
      goto(Active)

    case Event(Failure(f: ConversationFailed), MissionData(course, _)) =>
      stop using Finish(RunMissionFailed(vehicle, op, course, CourseError.SetupFailed,
        s"SetMode to Auto failed on ${f.request} with ${f.reply}"))
  }

  onTransition {
    case StartMission -> Active =>
      nextStateData match {
        case MissionData(course, _) => origin ! RunMissionStatus(vehicle, op, course, true)
        case _ => //
      }
  }

  when(Active, stateTimeout = operationSettings.telemetryTimeout) {
    case Event(Telemetry(_, state, _), MissionData(course, receivedSystemState)) =>
      state match {
        // only check if this is not the first SystemState, since it could have been sent before the mode change
        case s: SystemState if receivedSystemState && s.specificMode != Mode.Auto(vehicleType) =>
          stop using Finish(RunMissionResult(vehicle, op, course.completed))

        case s =>
          val newCourse = course.update(s)
          val newReceived = receivedSystemState || state.isInstanceOf[SystemState]

          if (newCourse eq course) {
            if (receivedSystemState == newReceived) {
              stay()
            } else {
              stay using MissionData(course, newReceived)
            }
          } else {
            if (newCourse.current != course.current) {
              origin ! RunMissionStatus(vehicle, op, newCourse, newCourse.waypoint != course.waypoint)
            }

            newCourse match {
              case nc if nc.isComplete =>
                stop using Finish(RunMissionResult(vehicle, op, nc))

              case nc if nc.status == CourseStatus.Error =>
                goto(AbortMission) using Finish(RunMissionFailed(vehicle, op, nc, CourseError.OffCourse,
                  "Vehicle is off course"))

              case nc =>
                stay using MissionData(nc, newReceived)
            }
          }
      }

    case Event(StateTimeout, MissionData(course, _)) =>
      stop using Finish(RunMissionFailed(vehicle, op, course, CourseError.TelemetryTimeout,
        s"Telemetry not received in ${ context.receiveTimeout }"))
  }

  onTransition {
    case _ -> AbortMission =>
      link.events.unsubscribe(self)
      vehicle.setMode(Mode.Loiter)
  }

  when(AbortMission) {
    case Event(_: ConversationSucceeded, finish: Finish) =>
      stop using finish

    case Event(Failure(r: ConversationFailed), finish: Finish) =>
      val error = s"Failed to set Loiter mode: ${ r.reply }"
      val newFinish = finish match {
        case Finish(fin: RunMissionResult) => RunMissionFailed(vehicle, fin.op, fin.last, CourseError.TeardownFailed, error)
        case Finish(fin: RunMissionFailed) => fin.copy(message = fin.message + " | " + error)
      }

      stop using Finish(newFinish)
  }


  /**
   * If actor is stopping unexpectedly and we were in the middle of a course,
   * set the vehicle to Loiter mode.
   */
  override def unexpectedStop(state: OpState, data: OpData): Unit = state match {
    case Active => vehicle.setMode(Mode.Loiter)
    case _ => //
  }
}
