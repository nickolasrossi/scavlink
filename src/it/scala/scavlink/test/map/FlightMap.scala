package scavlink.test.map

import scavlink._
import scavlink.coord.{Geo, LatLon}
import scavlink.link.fence.{TrafficControlActor, ProximityMonitor}
import scavlink.link.mission.Mission
import scavlink.message.VehicleId
import scavlink.message.enums.MavType
import akka.actor.ActorSystem

import scalafx.application.Platform
import scalafx.scene.web.WebEngine


class FlightMap(engine: WebEngine, flight: Flight) extends Runnable {

  def run(): Unit = {
    val system = ActorSystem("flight-map")
    val trafficControl = TrafficControlActor.initializer(ProximityMonitor(20, 1))
    val scavlink = ScavlinkInstance(system, initializers = trafficControl +: DefaultScavlinkInitializers)
    system.actorOf(FlightMapActor.props(scavlink, this, flight), "flight")
  }

  def js(script: String) = Platform.runLater { engine.executeScript(script) }

  def vehicleUp(vehicle: VehicleId, vehicleType: MavType.Value) =
    js(s"vehicleUp('$vehicle', '$vehicleType')")

  def vehicleDown(vehicle: VehicleId) =
    js(s"vehicleDown('$vehicle')")

  def drawMission(vehicle: VehicleId, number: Int, mission: Mission) = {
    val locations = mission.map(item => s"{ lat: ${item.x}, lng: ${item.y} }").mkString(", ")
    js(s"drawMission('$vehicle', $number, [ $locations ])")
  }

  def clearMission(vehicle: VehicleId) =
    js(s"clearMission('$vehicle')")

  def placeGuided(vehicle: VehicleId, number: Int, location: Geo) =
    js(s"placeGuided('$vehicle', $number, ${location.lat}, ${location.lon})")

  def clearGuided(vehicle: VehicleId) =
    js(s"clearGuided('$vehicle')")

  def drawPolygon(color: String, width: Int, coords: Seq[LatLon]) = {
    val lats = coords.map(p => s"${p.lat}").mkString(", ")
    val lons = coords.map(p => s"${p.lon}").mkString(", ")
    js(s"drawPolygon('$color', $width, [$lats], [$lons])")
  }

  def drawCircle(color: String, width: Int, center: LatLon, radius: Double) = {
    js(s"drawCircle('$color', $width, ${center.lat}, ${center.lon}, $radius)")
  }

  def positionUpdate(vehicle: VehicleId, location: Geo) =
    js(s"positionUpdate('$vehicle', ${location.lat}, ${location.lon}, ${location.alt})")

  def batteryUpdate(vehicle: VehicleId, level: Int) =
    js(s"batteryUpdate('$vehicle', $level)")

  def headingUpdate(vehicle: VehicleId, heading: Double) =
    js(s"headingUpdate('$vehicle', $heading)")

  def cogUpdate(vehicle: VehicleId, cog: Double) =
    js(s"cogUpdate('$vehicle', $cog)")

  def groundspeedUpdate(vehicle: VehicleId, groundspeed: Double, airspeed: Double) =
    js(s"speedUpdate('$vehicle', $groundspeed, $airspeed)")

  def modeUpdate(vehicle: VehicleId, mode: String) =
    js(s"modeUpdate('$vehicle', '$mode')")

  def throttleUpdate(vehicle: VehicleId, throttle: Double) =
    js(s"throttleUpdate('$vehicle', $throttle)")
}
