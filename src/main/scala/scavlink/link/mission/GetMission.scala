package scavlink.link.mission

import scavlink.link._
import scavlink.link.operation._
import scavlink.message.common._
import scavlink.message.enums.{MavComponent, MavMissionResult}
import scavlink.message.{ComponentId, Message, Packet, Unsigned}

/**
 * Retrieve the currently specified mission for the component.
 */
case class GetMission() extends MissionOp {
  val actorType = classOf[GetMissionActor]
}

case class GetMissionResult(vehicle: Vehicle, op: GetMission, mission: Mission) extends MissionOpResult


/**
 * Retrieves stored mission from the vehicle.
 * @see [[http://qgroundcontrol.org/mavlink/waypoint_protocol#read_mav_waypoint_list]]
 * @author Nick Rossi
 */
class GetMissionActor(vehicle: Vehicle) extends VehicleOpActor[GetMission](vehicle) {
  val mySystemId = vehicle.link.config.heartbeat.thisSystemId
  val missionComponent = ComponentId(MavComponent.MISSIONPLANNER)

  private case object ExpectingLength extends OpState

  private case class MissionData(mission: PartialMission, message: Message, tries: Int = 0) extends DefaultMessageRetry {
    def increment() = copy(tries = tries + 1)
  }


  // FSM states

  when(Idle) {
    case Event(op: GetMission, Uninitialized) =>
      start(op, sender())
      link.events.subscribe(self, SubscribeTo.messagesFrom(targetSystem, classOf[MissionCount], classOf[MissionItem]))
      goto(ExpectingLength) using MissionData(scala.collection.mutable.Seq.empty, MissionRequestList(targetSystem, missionComponent))
  }

  when(ExpectingLength) {
    case Event(Packet(_, msg: MissionCount), data) =>
      Unsigned(msg.count) match {
        case 0 =>
          link.send(MissionAck(targetSystem, missionComponent, MavMissionResult.ACCEPTED))
          stop using Finish(GetMissionResult(vehicle, op, Vector[MissionItem]()))

        case n =>
          goto(Active) using MissionData(scala.collection.mutable.Seq.fill(n)(None), MissionRequest(targetSystem, missionComponent, 0))
      }
  }

  when(Active) {
    case Event(Packet(_, msg: MissionItem), MissionData(mission, _, _)) =>
      Unsigned(msg.seq) match {
        case index if index < mission.length =>
          val oldValue = mission(index)
          mission(index) = Some(msg) // always use the latest value, even if we received this index already

          oldValue match {
            case None =>
              val n1 = index + 1
              val next = if (n1 < mission.length && mission(n1) == None) n1 else mission.indexOf(None)
              next match {
                case -1 =>
                  link.send(MissionAck(targetSystem, missionComponent, MavMissionResult.ACCEPTED))
                  stop using Finish(GetMissionResult(vehicle, op, mission.map(_.get).toVector))

                case i =>
                  goto(Active) using MissionData(mission, MissionRequest(targetSystem, missionComponent, i.toShort))
              }

            case _ => stay()
          }

        case index if index >= mission.length =>
          goto(ExpectingLength) using MissionData(scala.collection.mutable.Seq.empty, MissionRequestList(targetSystem, missionComponent))
      }
  }
}
