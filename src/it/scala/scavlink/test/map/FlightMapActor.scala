package scavlink.test.map

import akka.actor.{Actor, ActorLogging, Props}
import akka.util.Timeout
import com.spatial4j.core.shape.Circle
import com.spatial4j.core.shape.jts.JtsGeometry
import scavlink.ScavlinkInstance
import scavlink.connection.{LinkDown, LinkUp, VehicleDown, VehicleUp}
import scavlink.coord.{Formulas, LatLon}
import scavlink.link.fence.{Fence, FenceSettings, FenceUnion, ShapeFence}
import scavlink.link.mission.ReceivedMission
import scavlink.link.nav.RunGuidedCourseStatus
import scavlink.link.telemetry.TelemetryTellAPI._
import scavlink.link.telemetry._
import scavlink.link.{SentPacket, Vehicle}
import scavlink.message.{Mode, Packet, VehicleId}
import scavlink.state._

import scala.concurrent.duration._

object FlightMapActor {
  def props(mavlink: ScavlinkInstance, map: FlightMap, missions: Flight) =
    Props(classOf[FlightMapActor], mavlink, map, missions)
}

class FlightMapActor(scavlink: ScavlinkInstance, map: FlightMap, missions: Flight) extends Actor with ActorLogging {
  implicit val timeout = Timeout(2.minutes)
  implicit val executionContext = context.dispatcher
  private val telemetryInterval = 1000.milliseconds
  private val fenceLineWidth = 5

  private var vehicles: Map[VehicleId, Vehicle] = Map.empty
  private var fix: Set[VehicleId] = Set.empty
  private var markers: Map[VehicleId, LocationState] = Map.empty
  private var vehicleNumber: Map[VehicleId, Int] = Map.empty

  def nextVehicleNumber(): Int = {
    var i = 0
    while (vehicleNumber.exists { case (_, n) => n == i }) { i += 1 }
    i
  }

  def drawFence(fence: Fence): Unit = fence match {
    case f: ShapeFence =>
      f.shape match {
        case g: JtsGeometry =>
          val coords = g.getGeom.getCoordinates.map(c => LatLon(c.y, c.x))
          map.drawPolygon("white", fenceLineWidth, coords)

        case c: Circle =>
          val center = c.getCenter
          map.drawCircle("white", fenceLineWidth, LatLon(center.getY, center.getX),
            Formulas.lonDeltaToMeters(c.getRadius, center.getY))

        case _ => //
      }

    case f: FenceUnion =>
      drawFence(f.left)
      drawFence(f.right)

    case _ => //
  }

  override def preStart() = {
    val fences = FenceSettings(scavlink.config.root)
    fences.bindings.foreach(info => drawFence(info.fence))
    scavlink.events.subscribeToAll(self)
    missions.connectAll(scavlink)
  }

  override def postStop() = {
    scavlink.events.unsubscribe(self)
    vehicles.values foreach { api =>
      api.link.events.unsubscribe(self)
    }
  }

  def receive: Receive = {
    case LinkUp(link) =>
      log.debug(s"!!! Link up: $link")
      link.events.subscribeToAll(self)

    case LinkDown(link) =>
      log.debug(s"!!! Link down: $link")
      link.events.unsubscribe(self)

    case VehicleUp(vehicle) =>
      log.debug(s"!!! Vehicle up: $vehicle")
      val id = vehicle.id
      vehicles += id -> vehicle
      vehicleNumber += id -> nextVehicleNumber()
      map.vehicleUp(vehicle.id, vehicle.info.vehicleType)

    case VehicleDown(vehicle) =>
      log.debug(s"!!! Vehicle down: $vehicle")
      vehicles -= vehicle.id
      vehicleNumber -= vehicle.id
      map.vehicleDown(vehicle.id)

    case t@Telemetry(vehicle, gps: GpsState, _) if gps.fixType > GpsFixType.None =>
      log.debug(s"*** $t")
      val id = vehicle.id
      if (!fix.contains(id)) {
        fix += id
        context.system.scheduler.scheduleOnce(1.second) {
          missions.runFlight(vehicle)
        }
      }

    case t@Telemetry(vehicle, ls: LocationState, _) =>
      log.debug(s"*** $t")
      val id = vehicle.id
      map.positionUpdate(id, ls.location)
      map.headingUpdate(id, ls.heading)

    case t@Telemetry(vehicle, bs: BatteryState, _) =>
      log.debug(s"*** $t")
      val id = vehicle.id
      map.batteryUpdate(id, bs.remaining)

    case t@Telemetry(vehicle, ms: MotionState, _) =>
      log.debug(s"*** $t")
      val id = vehicle.id
      map.groundspeedUpdate(id, ms.groundspeed, ms.airspeed)
      map.cogUpdate(id, ms.courseOverGround)

    case t@Telemetry(vehicle, sys: SystemState, _) =>
      log.debug(s"*** $t")
      val id = vehicle.id
      val mode = Mode.from(vehicle.info.vehicleType, sys.specificMode)
      map.modeUpdate(id, mode.map(_.name).getOrElse("?"))

    case t@Telemetry(vehicle, cs: ChannelState, _) =>
      log.debug(s"*** $t")
      val id = vehicle.id
      val throttle = cs.throttle
      map.throttleUpdate(id, throttle)

    case ReceivedMission(vehicle, mission) =>
      val id = vehicle.id
      map.clearMission(id)
      map.drawMission(id, vehicleNumber(id), mission)

    case RunGuidedCourseStatus(vehicle, op, course, isNewWaypoint) if isNewWaypoint =>
      val number = vehicleNumber(vehicle.id)
      map.placeGuided(vehicle.id, number, course.waypoint)

    case p: Packet => log.debug(s"<-- $p")

    case SentPacket(p) => log.debug(s"  --> $p")

    case e => log.debug(s"*** $e")
  }
}