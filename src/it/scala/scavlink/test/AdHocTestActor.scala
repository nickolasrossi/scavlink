package scavlink.test

import akka.actor.{Actor, ActorLogging, Props}
import akka.pattern.ask
import akka.util.Timeout
import scavlink.connection._
import scavlink.coord.Geo
import scavlink.link._
import scavlink.link.channel.ChannelTellAPI._
import scavlink.link.mission.MissionAskAPI._
import scavlink.link.mission._
import scavlink.link.nav.NavAskAPI._
import scavlink.link.nav.{AreaCoverage, RotorTakeoffFailed}
import scavlink.link.operation.Conversation
import scavlink.link.parameter.ParameterAskAPI._
import scavlink.link.parameter._
import scavlink.link.telemetry.TelemetryTellAPI._
import scavlink.link.telemetry._
import scavlink.message.Packet
import scavlink.message.common._
import scavlink.state.{GpsFixType, GpsState}
import scavlink.test.map.SimFlight

import scala.concurrent.duration._

case class LoadParameters(vehicle: Vehicle)
case class LoadNamedParameters(vehicle: Vehicle, names: String*)
case class WriteParameter(vehicle: Vehicle, name: String, value: Float)
case class LoadMission(vehicle: Vehicle)
case class GoTo(vehicle: Vehicle, location: Geo)
case class RunMission(vehicle: Vehicle, mission: Mission)
case class WriteMission(vehicle: Vehicle, mission: Mission)
case class BlastCommands(vehicle: Vehicle)
case class GetAndSetParameter(vehicle: Vehicle)
case class SetDataStreams(vehicle: Vehicle)
case class SetOneDataStream(vehicle: Vehicle)
case class StopAllStreams(vehicle: Vehicle)
case class ArmDisarm(vehicle: Vehicle, arm: Boolean)
case class FenceTest(vehicle: Vehicle)
case class TryAPI(vehicle: Vehicle)
case class SetThrottle(vehicle: Vehicle, throttle: Int)
case class TakeoffTest(vehicle: Vehicle)
case class InvokeJson(json: String)

object AdHocTestActor {
  def props(events: ConnectionEventBus) = Props(classOf[AdHocTestActor], events)
}

class AdHocTestActor(events: ConnectionEventBus) extends Actor with ActorLogging {
  implicit val timeout = Timeout(2.minutes)
  implicit val executionContext = context.dispatcher

  val telemetryInterval = 500.milliseconds

  private var gpsFixType = GpsFixType.Unknown


  val mission = {
    val coverage = new AreaCoverage(SimFlight.sunnyvaleBaylandsWaypoints.map(_.latlon), 50)
    val points = coverage.transects(SimFlight.sunnyvaleBaylandsWaypoints(0).latlon, 50, true)
    val commands = points.take(6).map(NavWaypoint.apply) :+ NavReturnToLaunch()
    commandsToMission(commands)
  }

  val exec: Vehicle => AnyRef = vehicle => LoadMission(vehicle)


  override def preStart() = events.subscribeToAll(self)
  override def postStop() = events.unsubscribe(self)

  def receive: Receive = {
    case LinkUp(link) =>
      link.events.subscribeToAll(self)
      log.debug(s"!!! Link up: $link")

    case LinkDown(link) =>
      link.events.unsubscribe(self)
      log.debug(s"!!! Link down: $link")

    case VehicleUp(vehicle) =>
      log.debug(s"!!! Vehicle up: $vehicle")
      context.system.scheduler.scheduleOnce(3.seconds, self, exec(vehicle))

    case VehicleDown(vehicle) =>
      log.debug(s"!!! Vehicle down: $vehicle")

    case SentPacket(packet) => log.debug(s"  --> $packet")

    case packet: Packet =>
      log.debug(s"<-- $packet")

    case t@Telemetry(vehicle, gps: GpsState, _) =>
      log.debug(s"*** $t")
      if (gpsFixType != gps.fixType) {
        if (gps.fixType > GpsFixType.None && gpsFixType <= GpsFixType.None) {
          println("got fix!")
          self ! exec(vehicle)
        }

        gpsFixType = gps.fixType
      }

    case TakeoffTest(vehicle) =>
      val f = vehicle.rotorGentleTakeoff()
      f onSuccess {
        case r => println(s"takeoff happy: $r")
      }
      f onFailure {
        case r: RotorTakeoffFailed => println(s"takeoff sad: ${ r.message }")
      }


    case ArmDisarm(vehicle, arm) =>
      vehicle.armMotors(arm) onSuccess {
        case _ =>
          context.system.scheduler.scheduleOnce(4.seconds, self, SetThrottle(vehicle, 2))
      }

    case SetThrottle(vehicle, throttle) =>
      println(s"setting $throttle")
      vehicle.setThrottle(throttle)

    case SetDataStreams(vehicle) =>
      vehicle.setTelemetryStreams(DefaultTelemetryStreams.all, telemetryInterval, PublishImmediate)

    case SetOneDataStream(vehicle) =>
      val streams: StateGenerators = Set(DefaultTelemetryStreams.system)
      vehicle.setTelemetryStreams(streams, telemetryInterval)

    case StopAllStreams(vehicle) =>
      vehicle.stopAllTelemetry()

    case GoTo(vehicle, location) =>
      vehicle.armMotors(true) onSuccess {
        case r1 =>
          vehicle.setTelemetryStreams(DefaultTelemetryStreams.all, telemetryInterval, PublishOnInterval)
          vehicle.setThrottle(50)
          vehicle.gotoLocation(location) onComplete {
            case r2 => log.debug(s"gotoLocation: $r2")
          }
      }

    case LoadMission(vehicle) =>
      for {
        r1 <- vehicle.getMission
        r2 <- vehicle.getMission
      } {
        log.debug(s"### Got Mission(1): $r1")
        log.debug(s"### Got Mission(2): $r2")
      }

    case WriteMission(vehicle, m) =>
      for (r <- vehicle.setMission(m)) {
        context.system.scheduler.scheduleOnce(3.seconds) {
          (vehicle.navigation ? GetMission()) onSuccess {
            case r2 =>
              log.debug("mission get: " + r2)
          }
        }
      }

    case BlastCommands(vehicle) =>
      val (targetSystem, targetComponent) = vehicle.target

      val f = vehicle.holdConversation(Conversation.commands(
        DoFenceEnable(targetSystem, targetComponent, 1),
        DoParachute(targetSystem, targetComponent, 0),
        DoInvertedFlight(targetSystem, targetComponent, 0)
      ))

      f.onSuccess {
        case x => log.debug("commands succeeded: " + x)
      }
      f.onFailure {
        case x => log.debug("commands failed: " + x)
      }

    case LoadParameters(vehicle) =>
      vehicle.getAllParameters onSuccess {
        case GetAllParametersResult(_, _, params) =>
          params.filterKeys(_.startsWith("BAT")).foreach(println)
      }

    case LoadNamedParameters(vehicle, names@_*) =>
      vehicle.getNamedParameters(names.toSet) onComplete { r => log.debug(r.toString) }

    case WriteParameter(vehicle, name, value) =>
      val params: Parameters = Map(name -> value)
      vehicle.setParameters(params)

    case e => log.debug(s"*** $e")
  }
}
