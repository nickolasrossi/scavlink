package scavlink.task

import org.json4s.FieldSerializer._
import org.json4s.JsonDSL.WithDouble._
import org.json4s.ext.EnumNameSerializer
import org.json4s.jackson.Serialization
import org.json4s.{CustomSerializer, _}
import scavlink.coord.{Geo, LatLon}
import scavlink.link.Vehicle
import scavlink.message.common.MissionItem
import scavlink.message.enums.MavState
import scavlink.message.{Mode, Unsigned, VehicleId}
import scavlink.task.schema.{Pointer, PropertyType, Schema, TaskSchema}
import scavlink.task.service.Token

/**
 * Serializers for all types used in protocol messages.
 */
object Serializers {
  val all = Serialization.formats(NoTypeHints) +
            TaskSchemaSerializer +
            SchemaSerializer +
            PointerSerializer +
            VehicleSerializer +
            OAuth2TokenSerializer +
            ModeSerializer +
            GeoSerializer +
            LatLonSerializer +
            MissionItemSerializer +
            VehicleIdSerializer +
            new EnumNameSerializer(MavState)

  def methodToSchema(item: (String, Schema)): Schema = {
    val (name, schema) = item
    Schema(PropertyType.Object, Map(name -> Right(schema)), List(name))
  }


  object OAuth2TokenSerializer extends FieldSerializer[Token](
    renameTo("token", "access_token"),
    renameFrom("access_token", "token")
  )


  object TaskSchemaSerializer extends CustomSerializer[TaskSchema](formats => (PartialFunction.empty, {
    case rs: TaskSchema =>
      implicit val f = formats
      var json: JObject =
        ("$schema" -> rs.schema) ~
        ("type" -> PropertyType.Object.toString) ~
        ("title" -> rs.title) ~
        ("description" -> rs.description)

      if (rs.methods.nonEmpty) {
        val methods = rs.methods.map(methodToSchema)
        json = json ~ ("oneOf" -> Extraction.decompose(methods))
      }
      if (rs.definitions.nonEmpty) {
        json = json ~ ("definitions" -> Extraction.decompose(rs.definitions))
      }

      json
  }))


  object SchemaSerializer extends CustomSerializer[Schema](formats => (PartialFunction.empty, {
    case s: Schema =>
      implicit val f = formats
      var json: JObject = "type" -> s.typ.toString
      if (s.enum.nonEmpty) {
        json = json ~ ("enum" -> s.enum)
      }
      s.items foreach { items =>
        json = json ~ ("items" -> Extraction.decompose(items))
      }
      s.minItems foreach { minItems =>
        json = json ~ ("minItems" -> minItems)
      }
      s.maxItems foreach { maxItems =>
        json = json ~ ("maxItems" -> maxItems)
      }
      s.title foreach { title =>
        json = json ~ ("title" -> title)
      }
      s.description foreach { description =>
        json = json ~ ("description" -> description)
      }
      if (s.properties.nonEmpty) {
        json = json ~ ("properties" -> Extraction.decompose(s.properties))
      }
      if (s.patternProperties.nonEmpty) {
        json = json ~ ("patternProperties" -> Extraction.decompose(s.patternProperties))
      }
      if (s.required.nonEmpty) {
        json = json ~ ("required" -> s.required)
      }
      json
  }))


  object PointerSerializer extends CustomSerializer[Pointer](formats => (PartialFunction.empty, {
    case r: Pointer => "$ref" -> s"#/definitions/${ r.typ }"
  }))


  object VehicleSerializer extends CustomSerializer[Vehicle](formats => (PartialFunction.empty, {
    case v: Vehicle =>
      ("id" -> v.id.toString) ~
      ("type" -> v.info.vehicleType.toString) ~
      ("autopilot" -> v.info.autopilot.toString) ~
      ("group" -> v.info.number.group) ~
      ("number" -> v.info.number.systemId)
  }))


  object ModeSerializer extends CustomSerializer[Mode](formats => ( {
    case JString(s) if Mode.get(s).isDefined => Mode(s)
  }, {
    case mode: Mode => mode.name
    case s: String => s
  }))


  import org.json4s.DefaultReaders._

  val defaults = MissionItem()

  object GeoSerializer extends CustomSerializer[Geo](formats => ( {
    case arr: JArray => Geo(arr.as[Array[Double]])
  }, {
    case p: Geo => List[Double](p.lat, p.lon, p.alt)
  }))


  object LatLonSerializer extends CustomSerializer[LatLon](formats => ( {
    case arr: JArray => LatLon(arr.as[Array[Double]])
  }, {
    case p: LatLon => List[Double](p.lat, p.lon)
  }))


  object VehicleIdSerializer extends CustomSerializer[VehicleId](formats => ( {
    case JString(s) => VehicleId(s)
  }, {
    case v: VehicleId => v.toString
  }))


  object MissionItemSerializer extends CustomSerializer[MissionItem](formats => ( {
    case JObject(values) =>
      val map = values.toMap

      val seq = map.get("seq").map(_.as[Short])
      val command = map.get("command").map(_.as[Short])
      val frame = map.get("frame").map(_.as[Byte])
      val current = map.get("current").map(_.as[Byte])
      val autocontinue = map.get("autocontinue").map(_.as[Byte])
      val params = map.get("params").map(_.as[Array[Float]])

      def getParam(n: Int): Option[Float] = params.map(_.applyOrElse(n - 1, (_: Int) => 0f))

      MissionItem(
        seq = seq.getOrElse(defaults.seq),
        command = command.getOrElse(defaults.command),
        frame = frame.getOrElse(defaults.frame),
        current = current.getOrElse(defaults.current),
        autocontinue = autocontinue.getOrElse(defaults.autocontinue),
        param1 = getParam(1).getOrElse(defaults.param1),
        param2 = getParam(2).getOrElse(defaults.param2),
        param3 = getParam(3).getOrElse(defaults.param3),
        param4 = getParam(4).getOrElse(defaults.param4),
        x = getParam(5).getOrElse(defaults.x),
        y = getParam(6).getOrElse(defaults.y),
        z = getParam(7).getOrElse(defaults.z)
      )
  }, {
    case item: MissionItem =>
      ("seq" -> Unsigned(item.seq)) ~
      ("command" -> Unsigned(item.command)) ~
      ("frame" -> item.frame.toInt) ~
      ("current" -> item.current.toInt) ~
      ("autocontinue" -> item.autocontinue.toInt) ~
      ("params" -> List(item.param1, item.param2, item.param3, item.param4, item.x, item.y, item.z))
  }))
}
