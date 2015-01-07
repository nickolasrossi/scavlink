package scavlink.task.schema

/**
 * Represents a single JSON Schema definition.
 * Not all JSON Schema fields are provided here; just the ones we use.
 */
case class Schema(typ: PropertyType.Value,
                  properties: Map[String, Either[Pointer, Schema]] = Map.empty,
                  required: List[String] = List.empty,
                  patternProperties: Map[String, Either[Pointer, Schema]] = Map.empty,
                  enum: List[String] = List.empty,
                  items: Option[Either[Pointer, Schema]] = None,
                  minItems: Option[Int] = None,
                  maxItems: Option[Int] = None,
                  title: Option[String] = None,
                  description: Option[String] = None)

/**
 * Represents a JSON Pointer ("$ref" in JSON Schema document).
 */
case class Pointer(typ: String)


object PropertyType extends Enumeration {
  val Array = Value("array")
  val Boolean = Value("boolean")
  val Integer = Value("integer")
  val Number = Value("number")
  val Null = Value("null")
  val Object = Value("object")
  val String = Value("string")
}
