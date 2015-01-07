package scavlink.link.mission

import scavlink.link._
import scavlink.link.operation._
import scavlink.message.common._
import scavlink.message.enums.{MavComponent, MavMissionResult}
import scavlink.message.{ComponentId, Packet, Unsigned}

case class SetMission(mission: Mission, isPartial: Boolean = false) extends MissionOp {
  // require mission items to be in sequence
  require(mission.length <= MaxMissionItems)
  require(mission.zipWithIndex.forall { case (item, i) =>
    val requiredIndex = if (!isPartial) i else i + Unsigned(mission.head.seq)
    Unsigned(item.seq) == requiredIndex
  }, "Mission items must be in sequence")

  val actorType = classOf[SetMissionActor]
}

case class SetMissionResult(vehicle: Vehicle, op: SetMission, mission: Mission) extends MissionOpResult
case class SetMissionFailed(vehicle: Vehicle, op: SetMission, result: MavMissionResult.Value) extends OpException


/**
 * Writes the mission to the vehicle.
 * @see [[http://qgroundcontrol.org/mavlink/waypoint_protocol#write_mav_waypoint_list]]
 */
class SetMissionActor(vehicle: Vehicle) extends VehicleOpActor[SetMission](vehicle) {
  val mySystemId = vehicle.link.config.heartbeat.thisSystemId
  val missionComponent = ComponentId(MavComponent.MISSIONPLANNER)

  private case object Clearing extends OpState

  private var items: Map[Int, MissionItem] = Map.empty

  when(Idle) {
    case Event(op: SetMission, Uninitialized) =>
      start(op, sender())
      items = op.mission.map { item => Unsigned(item.seq) -> item }.toMap
      link.events.subscribe(self, SubscribeTo.messagesFrom(targetSystem, classOf[MissionRequest], classOf[MissionAck]))

      if (op.isPartial) {
        val seqs = items.keys
        val message = MissionWritePartialList(targetSystem, missionComponent, seqs.min.toShort, (seqs.max + 1).toShort)
        goto(Active) using MessageData(message)
      } else {
        val message = MissionClearAll(targetSystem, missionComponent)
        goto(Clearing) using MessageData(message)
      }
  }

  when(Clearing) {
    case Event(Packet(_, MissionAck(_, _, result)), _) =>
      result match {
        case MavMissionResult.ACCEPTED =>
          if (op.mission.length > 0) {
            goto(Active) using MessageData(MissionCount(targetSystem, missionComponent, op.mission.length.toShort))
          } else {
            stop using Finish(SetMissionResult(vehicle, op, Vector.empty))
          }

        case r =>
          stop using Finish(SetMissionFailed(vehicle, op, r))
      }
  }

  when(Active) {
    case Event(Packet(_, MissionRequest(_, _, seq)), _) =>
      val index = Unsigned(seq)
      if (items.contains(index)) {
        stay using MessageData(items(index).setTarget(targetSystem, missionComponent))
      } else {
        stop using Finish(SetMissionFailed(vehicle, op, MavMissionResult.INVALID_SEQUENCE))
      }

    case Event(Packet(_, MissionAck(forSystem, forComponent, r)), _) =>
      val result = r match {
        case MavMissionResult.ACCEPTED =>
          SetMissionResult(vehicle, op, op.mission.map(_.setTarget(forSystem, forComponent)))

        case _ =>
          SetMissionFailed(vehicle, op, r)
      }

      stop using Finish(result)
  }
}
