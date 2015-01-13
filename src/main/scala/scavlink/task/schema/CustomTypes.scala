package scavlink.task.schema

import scavlink.coord.{Geo, LatLon}
import scavlink.link.Vehicle
import scavlink.message.Mode
import scavlink.message.common.MissionItem

import scala.collection.immutable.ListMap
import scala.reflect.runtime.universe._

/**
 * Schemas for local types that are allowed to be API method arguments.
 */
object CustomTypes {
  val vehicle = typeOf[Vehicle] -> Schema(PropertyType.String)

  val geo = typeOf[Geo] -> Schema(PropertyType.Array,
    items = Some(Right(Schema(PropertyType.Number))), minItems = Some(3), maxItems = Some(3))

  val latlon = typeOf[LatLon] -> Schema(PropertyType.Array,
    items = Some(Right(Schema(PropertyType.Number))), minItems = Some(2), maxItems = Some(2))

  val mode = typeOf[Mode] -> Schema(PropertyType.String,
    enum = Mode.values.keySet.toList)

  val missionItem = typeOf[MissionItem] -> Schema(PropertyType.Object,
    properties = ListMap(
      "seq" -> Right(Schema(PropertyType.Integer)),
      "command" -> Right(Schema(PropertyType.Integer)),
      "frame" -> Right(Schema(PropertyType.Integer)),
      "current" -> Right(Schema(PropertyType.Integer)),
      "autocontinue" -> Right(Schema(PropertyType.Integer)),
      "params" -> Right(Schema(PropertyType.Array,
        items = Some(Right(Schema(PropertyType.Number))), minItems = Some(7), maxItems = Some(7)))
    ),
    required = List("seq", "command", "frame", "current", "autocontinue", "params"))


  val allTypes = List(vehicle, geo, latlon, mode, missionItem).toMap
  val allNames = allTypes.map { case (k, v) => (nameOf(k), v) }

  def nameOf(typ: Type): String = typ.typeSymbol.name.decodedName.toString
}
