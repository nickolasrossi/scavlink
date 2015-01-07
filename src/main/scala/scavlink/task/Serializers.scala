package scavlink.task

import org.json4s.FieldSerializer._
import org.json4s.JsonDSL.WithDouble._
import org.json4s.jackson.Serialization
import org.json4s.{CustomSerializer, _}
import scavlink.link.Vehicle
import scavlink.task.schema.{Pointer, PropertyType, Schema, TaskSchema}
import scavlink.task.service.Token

/**
 * Serializers for all possible responses from server to client.
 * TODO: Combine server-to-client and client-to-server serializers in one list
 */
object Serializers {
  val all = Serialization.formats(NoTypeHints) +
            TaskSchemaSerializer +
            SchemaSerializer +
            PointerSerializer +
            VehicleSerializer +
            OAuth2TokenSerializer +
            TaskWorkerResponseSerializer +
            new FieldSerializer[TaskProgress] +
            new FieldSerializer[TaskComplete]


  object TaskWorkerResponseSerializer extends CustomSerializer[TaskInvokerSession.Response](formats => (PartialFunction.empty, {
    case response: TaskInvokerSession.Response =>
      implicit val f = formats
      val field = if (response.name.length > 0) response.name
      else {
        val className = response.obj.getClass.getSimpleName
        className.head.toLower + className.tail
      }

      field -> Extraction.decompose(response.obj)
  }))


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
      ("link" -> v.link.address) ~
      ("type" -> v.info.vehicleType.toString) ~
      ("autopilot" -> v.info.autopilot.toString) ~
      ("group" -> v.info.number.group) ~
      ("number" -> v.info.number.systemId)
  }))


  def methodToSchema(item: (String, Schema)): Schema = {
    val (name, schema) = item
    Schema(PropertyType.Object, Map(name -> Right(schema)), List(name))
  }
}
