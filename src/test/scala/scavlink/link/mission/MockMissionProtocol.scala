package scavlink.link.mission

import akka.actor.Actor.Receive
import scavlink.link.{MockVehicle, SentPacket}
import scavlink.message._
import scavlink.message.common._
import scavlink.message.enums.MavMissionResult

/**
 * Acts like the mission store for a vehicle autopilot.
 */
trait MockMissionProtocol {
  self: MockVehicle =>

  type PartialMission = scala.collection.mutable.Seq[Option[MissionItem]]
  private val emptyMission: PartialMission = scala.collection.mutable.Seq.empty

  def initialResultSet: Mission
  private var mission: Mission = initialResultSet
  private var receivingMission: PartialMission = emptyMission

  // simulate real autopilot by returning mission messages from component = 1
  val thisComponent = ComponentId(1)
  
  def send(msg: TargetComponent[_ <: Message], fromPacket: Packet, fromMessage: TargetComponent[_]): Unit = {
    val from = From(VehicleId.fromLink(address, fromMessage.targetSystem), fromMessage.targetSystem, thisComponent)
    send(Packet(from, msg.setTarget(fromPacket.from.systemId, fromPacket.from.componentId)))
  }

  def missionHandler: Receive = {
    // get

    case SentPacket(packet@Packet(from, msg: MissionRequestList)) =>
      send(MissionCount(count = mission.length.toShort), packet, msg)

    case SentPacket(packet@Packet(from, msg@MissionRequest(_, _, seq))) =>
      send(mission(Unsigned(seq)), packet, msg)


    // set

    case SentPacket(packet@Packet(from, msg: MissionClearAll)) =>
      mission = Vector.empty
      send(MissionAck(`type` = MavMissionResult.ACCEPTED), packet, msg)

    case SentPacket(packet@Packet(from, msg@MissionCount(_, _, count))) =>
      receivingMission = scala.collection.mutable.Seq.fill(Unsigned(count))(None)
      send(MissionRequest(seq = 0), packet, msg)

    case SentPacket(packet@Packet(from, msg@MissionWritePartialList(_, _, _first, _last))) =>
      val first = Unsigned(_first)
      val last = if (_first == _last) Unsigned(_last + 1) else Unsigned(_last)

      receivingMission = scala.collection.mutable.Seq.tabulate(mission.length) { i =>
        if (i >= first && i < last) None else Some(mission(i))
      }

      send(MissionRequest(seq = _first), packet, msg)

    case SentPacket(packet@Packet(from, msg: MissionItem)) =>
      val index = Unsigned(msg.seq)
      if (index < receivingMission.length) {
        receivingMission(index) = Some(msg.setTarget(SystemId.zero, ComponentId.zero))
        val n1 = index + 1
        val next = if (n1 < receivingMission.length && receivingMission(n1) == None) n1 else receivingMission.indexOf(None)

        if (next >= 0) {
          send(MissionRequest(seq = next.toShort), packet, msg)
        } else {
          mission = receivingMission.map(_.get).toVector
          receivingMission = emptyMission
          send(MissionAck(`type` = MavMissionResult.ACCEPTED), packet, msg)
        }
      } else {
        send(MissionAck(`type` = MavMissionResult.ERROR), packet, msg)
      }
  }
}