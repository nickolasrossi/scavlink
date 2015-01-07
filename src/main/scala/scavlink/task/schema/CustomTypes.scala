package scavlink.task.schema

import org.json4s.JsonAST.JObject
import org.json4s.JsonDSL.WithDouble._
import org.json4s._
import scavlink.coord.{LatLon, Geo}
import scavlink.link.Vehicle
import scavlink.message.{Mode, Unsigned, VehicleId}
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
      "command" -> Right(Schema(PropertyType.Integer)),
      "frame" -> Right(Schema(PropertyType.Integer)),
      "current" -> Right(Schema(PropertyType.Integer)),
      "autocontinue" -> Right(Schema(PropertyType.Integer)),
      "params" -> Right(Schema(PropertyType.Array,
        items = Some(Right(Schema(PropertyType.Number))), minItems = Some(7), maxItems = Some(7)))
    ),
    required = List("command", "frame", "current", "autocontinue", "params"))


  val allTypes = List(vehicle, geo, latlon, mode, missionItem).toMap
  val allNames = allTypes.map { case (k, v) => (nameOf(k), v) }

  def nameOf(typ: Type): String = typ.typeSymbol.name.decodedName.toString

}


/**
 * Serializers for types allowed from client to server as API method arguments.
 */
object CustomTypeSerializers {
  def serializers = List(ModeSerializer, GeoSerializer, LatLonSerializer, MissionItemSerializer)

  object ModeSerializer extends Serializer[Mode] {
    def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), Mode] = {
      case (_, JString(s)) if Mode.get(s).isDefined => Mode(s)
    }

    def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
      case mode: Mode => mode.name
      case s: String => s
    }
  }


  object GeoSerializer extends Serializer[Geo] {

    import org.json4s.DefaultReaders._

    def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), Geo] = {
      case (_, arr: JArray) => Geo(arr.as[Array[Double]])
    }

    def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
      case p: Geo => List[Double](p.lat, p.lon, p.alt)
    }
  }

  object LatLonSerializer extends Serializer[LatLon] {

    import org.json4s.DefaultReaders._

    def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), LatLon] = {
      case (_, arr: JArray) => LatLon(arr.as[Array[Double]])
    }

    def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
      case p: LatLon => List[Double](p.lat, p.lon)
    }
  }


  class VehicleIdSerializer(vehicles: Map[VehicleId, Vehicle]) extends Serializer[Vehicle] {
    def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), Vehicle] = {
      case (_, JString(s)) if vehicles.isDefinedAt(VehicleId(s)) => vehicles(VehicleId(s))
    }

    def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
      case v: Vehicle => v.id.toString
      case v: VehicleId => v.toString
    }
  }


  object MissionItemSerializer extends Serializer[MissionItem] {

    import org.json4s.DefaultReaders._

    val defaults = MissionItem()

    def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), MissionItem] = {
      case (_, JObject(values)) =>
        val map = values.toMap

        val seq = map.get("seq").map(_.as[Short])
        val command = map.get("command").map(_.as[Short])
        val frame = map.get("frame").map(_.as[Byte])
        val current = map.get("current").map(_.as[Byte])
        val autocontinue = map.get("autocontinue").map(_.as[Byte])
        val params = map.get("params").map(_.as[Array[Float]])

        def getElement(n: Int): Option[Float] = params.map(_.applyOrElse(n - 1, (_: Int) => 0f))

        MissionItem(
          seq = seq.getOrElse(defaults.seq),
          command = command.getOrElse(defaults.command),
          frame = frame.getOrElse(defaults.frame),
          current = current.getOrElse(defaults.current),
          autocontinue = autocontinue.getOrElse(defaults.autocontinue),
          param1 = getElement(1).getOrElse(defaults.param1),
          param2 = getElement(2).getOrElse(defaults.param2),
          param3 = getElement(3).getOrElse(defaults.param3),
          param4 = getElement(4).getOrElse(defaults.param4),
          x = getElement(5).getOrElse(defaults.x),
          y = getElement(6).getOrElse(defaults.y),
          z = getElement(7).getOrElse(defaults.z)
        )
    }

    def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
      case item: MissionItem =>
        ("seq" -> Unsigned(item.seq)) ~
        ("command" -> Unsigned(item.command)) ~
        ("frame" -> item.frame.toInt) ~
        ("current" -> item.current.toInt) ~
        ("autocontinue" -> item.autocontinue.toInt) ~
        ("params" -> List(item.param1, item.param2, item.param3, item.param4, item.x, item.y, item.z))
    }
  }
}