package scavlink.link

import akka.actor.{ActorRef, ActorRefFactory}
import scavlink.VehicleInitializer
import scavlink.connection.VehicleNumber
import scavlink.link.channel.ChannelOverrideActor
import scavlink.link.mission.MissionCacheActor
import scavlink.link.nav.NavOpSupervisor
import scavlink.link.parameter.ParameterCacheActor
import scavlink.link.telemetry.TelemetryActor
import scavlink.message.enums.{MavAutopilot, MavType}
import scavlink.message.{ComponentId, SystemId, VehicleId}

/**
 * Canonical representation of a single vehicle.
 * Holds vehicle properties and ActorRefs for all vehicle-specific actors.
 *
 * APIs for vehicle operations are provided as implicit classes referencing this Vehicle class.
 * Application code should import the desired implicit class API wherever needed,
 * then call API methods that become available on the Vehicle object.
 *
 * APIs come in two flavors: a tell-based API that simply sends Akka messages,
 * and an ask-based API that returns futures over the result.
 * @see [[scavlink.link.nav.NavTellAPI]] and [[scavlink.link.nav.NavAskAPI]] as examples.
 *
 * The tell APIs are preferable, especially when calling from inside an actor, because
 * there's no need to create a private actor to receive the result, and progress messages
 * can be received during execution.
 *
 * @author Nick Rossi
 */
class Vehicle(val info: VehicleInfo, val link: Link, initializers: Seq[VehicleInitializer])(implicit val context: ActorRefFactory) {
  val id = info.id
  val target = (info.systemId, info.defaultComponentId)
  val settings = VehicleSettings(link.config.root)

  // start separate actors that may execute actions in parallel with each other
  val navigation = context.actorOf(NavOpSupervisor.props(this), "navigation")
  val telemetry = context.actorOf(TelemetryActor.props(this), "telemetry")
  val parameterCache = context.actorOf(ParameterCacheActor.props(this), "parameter-cache")
  val missionCache = context.actorOf(MissionCacheActor.props(this), "mission-cache")
  val channels = context.actorOf(ChannelOverrideActor.props(this), "channel")

  private val actors = initializers.map(init => init(this, context)).flatten.map(a => a.path.name -> a).toMap

  /**
   * Return an actor that was started by a custom initializer.
   * @param name actor name
   */
  def actorFor(name: String): ActorRef = actors(name)

  override def toString = info.toString
}

/**
 * Metadata about a single vehicle.
 * @param id unique vehicle id within the environment
 * @param number unique vehicle number within the local application
 * @param systemId MAVLink systemId
 * @param defaultComponentId MAVLink componentId from heartbeat
 * @param vehicleType type of vehicle (copter, rover, boat, etc)
 * @param autopilot type of autopilot
 */
case class VehicleInfo(id: VehicleId,
                       number: VehicleNumber,
                       systemId: SystemId,
                       defaultComponentId: ComponentId,
                       vehicleType: MavType.Value,
                       autopilot: MavAutopilot.Value,
                       authKey: Option[String]) {
  val typeInfo = VehicleType(vehicleType)
  override def toString = s"$id ($systemId,$defaultComponentId,$vehicleType,$autopilot)"
}
