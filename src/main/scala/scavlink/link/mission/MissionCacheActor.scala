package scavlink.link.mission

import akka.actor.{Props, ReceiveTimeout}
import scavlink.link._
import scavlink.message.common.{MissionCount, MissionItem, MissionRequest}
import scavlink.message.{Packet, Unsigned}

import scala.concurrent.duration._

/**
 * Published whenever the mission is returned or updated.
 */
case class ReceivedMission(vehicle: Vehicle, mission: Mission) extends LinkEvent
/**
 * Published when mission cache is evicted due to updates detected from another client.
 */
case class ExternalMissionUpdate(vehicle: Vehicle) extends LinkEvent

case object ClearCache


object MissionCacheActor {
  def props(vehicle: Vehicle) = Props(classOf[MissionCacheActor], vehicle)
}

/**
 * Wraps a cache around mission operations.
 *
 * GetMission returns a cache entry if present, retrieves the mission and updates the cache entry if not.
 * SetMission updates the cache entry upon success.
 *
 * The cache implements OpSupervisor, so operations that would result in a cache miss cannot be called simultaneously.
 * However, for any request that is a cache hit, no operation is invoked, in which case there's no restriction.
 *
 * If a MissionAck message is noticed for a mission currently in the cache, and there are no pending SetMission
 * operations, it means that some other vehicle on the connection updated the mission. In that case, GetMission
 * is invoked for a cache update.
 *
 * @author Nick Rossi
 */
class MissionCacheActor(vehicle: Vehicle) extends VehicleOpSupervisor[MissionOp](vehicle) {
  val opClass = classOf[MissionOp]

  val link = vehicle.link
  val id = vehicle.id
  val target = vehicle.info.systemId

  private var missionCache: Option[Mission] = None
  private var itemBuffer: PartialMission = scala.collection.mutable.Seq.empty
  private val unknownIdleTimeout = 3.seconds

  override def preStart() = {
    link.events.subscribe(self, SubscribeTo.messagesFrom(target,
      classOf[MissionCount], classOf[MissionItem], classOf[MissionRequest]))

    if (vehicle.settings.autoloadMission) self ! GetMission()
  }

  override def postStop() = link.events.unsubscribe(self)

  def updateCache(mission: Option[Mission]) = {
    missionCache = mission
    itemBuffer = scala.collection.mutable.Seq.empty
    mission foreach { m =>
      link.events.publish(ReceivedMission(vehicle, m))
    }
  }

  /**
   * If the new mission is similar to the old, convert op to a partial write.
   */
  def optimize(op: SetMission, cached: Mission): Option[SetMission] = {
    if (op.isPartial || op.mission.length == 0 || cached.length == 0 ||
      op.mission.length != cached.length) return Some(op)

    val mission = op.mission.map(_.setTargetSystem(cached.head.targetSystem))
    val updates = mission.diff(cached)
    if (updates.length == mission.length) return Some(op)
    if (updates.isEmpty) return None

    val first = Unsigned(updates.head.seq)
    val last = Unsigned(updates.last.seq) + 1
    if (first == 0 && last == mission.length) return Some(op)

    Some(SetMission(op.mission.slice(first, last), true))
  }

  override def opHandler: Receive = {
    case ClearCache =>
      updateCache(None)

    case op: GetMission =>
      missionCache match {
        case Some(mission) => sender ! GetMissionResult(vehicle, op, mission)
        case None => submit(op)
      }

    case op: SetMission =>
      missionCache match {
        case Some(cached) =>
          optimize(op, cached) match {
            case Some(opt) =>
              // as soon as the op sends a MissionCount message, the firmware may change
              // the on-board mission length before the MissionAck, which means our cache
              // is invalid immediately - we can't keep it even if SetMission ultimately fails
              if (!opt.isPartial) updateCache(None)
              submit(opt)

            case None =>
              sender ! SetMissionResult(vehicle, op, cached)
          }

        case None =>
          submit(op)
      }

    case result: SetMissionResult =>
      missionCache match {
        case Some(cached) =>
          val newMission = if (result.op.isPartial) {
            val first = Unsigned(result.mission.head.seq)
            val last = Unsigned(result.mission.last.seq)
            cached.slice(0, first) ++ result.mission ++ cached.slice(last + 1, cached.length)
          } else {
            result.op.mission
          }

          updateCache(Some(newMission))

        case None =>
          if (!result.op.isPartial) updateCache(Some(result.mission))
      }

      finish(result)

    case result: GetMissionResult =>
      updateCache(Some(result.mission))
      finish(result)


    // monitor Mission messages when no operations are pending

    case Packet(_, msg: MissionCount) if !opsPending =>
      itemBuffer = scala.collection.mutable.Seq.fill(Unsigned(msg.count))(None)

    case Packet(_, msg: MissionRequest) if !opsPending =>
      updateCache(None)
      context.setReceiveTimeout(unknownIdleTimeout)

    case Packet(_, msg: MissionItem) if !opsPending =>
      val seq = Unsigned(msg.seq)
      if (seq >= itemBuffer.length) itemBuffer = itemBuffer.padTo(seq + 1, None)

      itemBuffer(seq) = Some(msg)
      context.setReceiveTimeout(unknownIdleTimeout)

    case ReceiveTimeout =>
      context.setReceiveTimeout(Duration.Undefined)
      if (itemBuffer.nonEmpty && !itemBuffer.contains(None)) {
        val mission = itemBuffer.collect { case Some(item) => item }
        updateCache(Some(mission.toVector))
      } else {
        updateCache(None)
        link.events.publish(ExternalMissionUpdate(vehicle))
      }
  }
}
