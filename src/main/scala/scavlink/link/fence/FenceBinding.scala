package scavlink.link.fence

import scavlink.link.{VehicleInfo, VehicleType}
import com.typesafe.config.Config

import scala.collection.JavaConversions._


case class FenceBinding(name: String, fence: Fence, mode: FenceMode.Value, applyTo: VehicleInfo => Boolean)

/**
 * Binds defined fences to vehicles by name or type with a predicate,
 * and assigns the type of fence enforcement for the binding (stay in, stay out, or report).
 */
object FenceBinding {
  val ConfigVehicleType = "vehicle-type"
  val ConfigVehicleTypes = "vehicle-types"
  val ConfigVehicleName = "vehicle-name"
  val ConfigVehiclePrefix = "vehicle-name-prefix"

  def apply(fences: Map[String, Fence], config: Config): Set[FenceBinding] = {
    if (fences.nonEmpty && config.hasPath("bind")) {
      val list = config.getConfigList("bind")
      list.map(parseBinding(fences)).toSet
    } else {
      Set.empty
    }
  }

  def parseBinding(fences: Map[String, Fence])(config: Config): FenceBinding = {
    val name = config.getString("fence")
    val fence = fences(name)
    val mode = FenceMode.withName(config.getString("mode"))
    val apply = parsePredicate(config)
    FenceBinding(name, fence, mode, apply)
  }
  
  /**
   * Parse a predicate that determines which vehicles the binding will apply to.
   */
  def parsePredicate(config: Config): (VehicleInfo => Boolean) = {
    if (config.hasPath(ConfigVehicleType)) {
      info: VehicleInfo => VehicleType(info.vehicleType).is(config.getString(ConfigVehicleType))
    } else if (config.hasPath(ConfigVehicleTypes)) {
      info: VehicleInfo => VehicleType(info.vehicleType).is(config.getStringList(ConfigVehicleTypes))
    } else if (config.hasPath(ConfigVehicleName)) {
      info: VehicleInfo => info.id.address == config.getString(ConfigVehicleName)
    } else if (config.hasPath(ConfigVehiclePrefix)) {
      info: VehicleInfo => info.id.address.startsWith(config.getString(ConfigVehiclePrefix))
    } else {
      info => true
    }
  }

  /**
   * Filter fences for a particular vehicle.
   * @param fences full fence set
   * @param vehicleInfo vehicle
   * @return filtered fence set
   */
  def filter(fences: Set[FenceBinding], vehicleInfo: VehicleInfo): Set[FenceBinding] = {
    val applicableFences = fences.filter(_.applyTo(vehicleInfo)).toVector
    
    // predicates may result in multiple fence modes for the same vehicle,
    // so we sort to put the most restrictive modes last
    val sorted = applicableFences.sortWith {
      (f1, f2) => f1.fence == f2.fence && f1.mode > f2.mode
    }
    
    // when fences are collected into a map, most restrictive mode per vehicle wins
    sorted.map(fence => fence -> fence.mode).toMap.keySet
  }
}
