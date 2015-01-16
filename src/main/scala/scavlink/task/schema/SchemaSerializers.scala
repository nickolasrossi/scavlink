package scavlink.task.schema

import org.json4s._
import org.json4s.JsonDSL.WithDouble._

object SchemaSerializers {

  object RootSchemaSerializer extends CustomSerializer[RootSchema](formats => (PartialFunction.empty, {
    case rs: RootSchema =>
      implicit val f = formats
      var json: JObject =
        ("$schema" -> rs.schema) ~
        ("type" -> PropertyType.Object.toString) ~
        ("title" -> rs.title)

      rs.description foreach { description =>
        json ~= ("description" -> rs.description)
      }

      if (rs.messages.nonEmpty) {
        json ~= ("oneOf" -> Extraction.decompose(rs.messages))
      }

      json
  }))


  object SchemaSerializer extends CustomSerializer[Schema](formats => (PartialFunction.empty, {
    case s: Schema =>
      implicit val f = formats
      var json: JObject =
        if (s.typ == PropertyType.Any) {
          "type" -> PropertyType.values.map(_.toString)
        } else {
          "type" -> s.typ.toString
        }

      if (s.enum.nonEmpty) {
        json ~= ("enum" -> s.enum)
      }
      s.items foreach { items =>
        json ~= ("items" -> Extraction.decompose(items))
      }
      s.minItems foreach { minItems =>
        json ~= ("minItems" -> minItems)
      }
      s.maxItems foreach { maxItems =>
        json ~= ("maxItems" -> maxItems)
      }
      s.title foreach { title =>
        json ~= ("title" -> title)
      }
      s.description foreach { description =>
        json ~= ("description" -> description)
      }
      if (s.properties.nonEmpty) {
        json ~= ("properties" -> Extraction.decompose(s.properties))
      }
      if (s.patternProperties.nonEmpty) {
        json ~= ("patternProperties" -> Extraction.decompose(s.patternProperties))
      }
      if (s.required.nonEmpty) {
        json ~= ("required" -> s.required)
      }
      s.additionalProperties foreach { add =>
        json ~= ("additionalProperties" -> add)
      }
      if (s.oneOf.nonEmpty) {
        json ~= ("oneOf" -> Extraction.decompose(s.oneOf))
      }
      json
  }))


  object PointerSerializer extends CustomSerializer[Pointer](formats => (PartialFunction.empty, {
    case r: Pointer => "$ref" -> s"types#/${ r.typ }"
  }))
}
