package scavlink.task.schema

import scavlink.task._

import scala.collection.immutable.ListMap

/**
 * Simplified definition for top-level request, response, and type reference schemas.
 */
case class RootSchema(title: String,
                      description: Option[String],
                      messages: List[Schema]) {
  val schema = "http://json-schema.org/draft-04/schema#"
}


object RootSchema {
  def requests(apis: Seq[TaskAPI]): RootSchema = {
    val schemas = List(
      Schema.singleProperty(propertyNameOf[StartTask], Schema.fromAPIs(apis)),
      Schema.singleProperty(propertyNameOf[StopTask],
        Schema(PropertyType.Object, ListMap("context" -> Right(Schema(PropertyType.Any))), List("context"))),
      Schema.fromClass[StartTelemetry],
      Schema.fromClass[StopTelemetry]
    )

    RootSchema("requests", None, schemas.map(addContext))
  }

  def responses(): RootSchema = {
    val schemas = List(
      Schema.fromClass[VehicleUp],
      Schema.fromClass[VehicleDown],
      Schema.fromClass[StatusMessage],
      Schema.fromClass[Telemetry],
      addContext(Schema.fromClass[TaskProgress]),
      addContext(Schema.fromClass[TaskComplete]),
      addContext(Schema.singleProperty("error", Schema(PropertyType.String)))
    )

    RootSchema("responses", None, schemas)
  }

  private def addContext(schema: Schema): Schema =
    schema.copy(properties = ListMap("context" -> Right(Schema(PropertyType.Any))) ++ schema.properties)
}