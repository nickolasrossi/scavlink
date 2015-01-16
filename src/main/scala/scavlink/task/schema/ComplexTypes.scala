package scavlink.task.schema

import scavlink.coord.{Geo, LatLon}
import scavlink.link.Vehicle
import scavlink.message.{VehicleId, Mode}
import scavlink.message.common.MissionItem

import scala.collection.immutable.ListMap
import scala.reflect.runtime.universe._

/**
 * Schemas for local types that are allowed to be API method arguments.
 */
object ComplexTypes {
  private val vehicleId = typeOf[VehicleId] -> Schema(PropertyType.String)

  private val vehicle = typeOf[Vehicle] -> Schema(PropertyType.Object,
    properties = ListMap(
      "id" -> Right(Schema(PropertyType.String)),
      "type" -> Right(Schema(PropertyType.String)),
      "autopilot" -> Right(Schema(PropertyType.String)),
      "group" -> Right(Schema(PropertyType.Integer)),
      "number" -> Right(Schema(PropertyType.Integer))
    )
  )

  private val geo = typeOf[Geo] -> Schema(PropertyType.Array,
    items = Some(Right(Schema(PropertyType.Number))), minItems = Some(3), maxItems = Some(3))

  private val latlon = typeOf[LatLon] -> Schema(PropertyType.Array,
    items = Some(Right(Schema(PropertyType.Number))), minItems = Some(2), maxItems = Some(2))

  private val mode = typeOf[Mode] -> Schema(PropertyType.String,
    enum = Mode.values.keySet.toList)

  private val missionItem = typeOf[MissionItem] -> Schema(PropertyType.Object,
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


  val types = List(vehicleId, vehicle, geo, latlon, mode, missionItem).toMap

  val schemas = types map { case (typ, schema) =>
    (definitionNameOf(typ), schema)
  }
}
