package scavlink.task.schema

import scavlink.task.{APIMethod, TaskAPI}

import scala.collection.immutable.ListMap
import scala.reflect.runtime.universe._

/**
 * Simplified schema representation for the root level of all task method definitions.
 * @author Nick Rossi
 */
case class TaskSchema(methods: Map[String, Schema],
                      definitions: Map[String, Schema]) {
  val schema = "http://json-schema.org/draft-04/schema#"
  val title = "Scavlink tasks"
  val description = "Tasks available for execution on this service"
}


object TaskSchema extends App {
  /**
   * Build a combined task schema from a list of task APIs.
   */
  def apply(apis: Seq[TaskAPI]): TaskSchema = {
    val schemas = apis.foldLeft(ListMap.empty[String, Schema])(_ ++ apiToSchemas(_))
    TaskSchema(schemas, CustomTypes.allNames)
  }

  private[schema] def apiToSchemas(api: TaskAPI): Map[String, Schema] = {
    val mtos = methodToSchema(api.ctor) _
    api.methods.map { case (name, method) =>
      val qualifiedName = api.name + "." + name
      (qualifiedName, mtos(method))
    }
  }

  private[schema] def methodToSchema(ctor: APIMethod)(method: APIMethod): Schema = {
    val ctorProperties = methodProperties(ctor)
    val properties = methodProperties(method)
    val required = ctor.params.keySet ++ method.params.keySet -- method.defaults.keySet -- ctor.defaults.keySet
    Schema(PropertyType.Object, ctorProperties ++ properties, required.toList)
  }

  private[schema] def methodProperties(method: APIMethod): ListMap[String, Either[Pointer, Schema]] =
    method.params.map {
      case (k, v) => (k, typeToProperty(v))
    }.collect {
      case (k, Some(v)) => (k, v)
    }

  private[schema] def typeToProperty(typ: Type): Option[Either[Pointer, Schema]] = {
    if (typ weak_<:< typeOf[Long]) Some(Right(Schema(PropertyType.Integer)))
    else if (typ weak_<:< typeOf[Double]) Some(Right(Schema(PropertyType.Number)))
    else if (typ =:= typeOf[String]) Some(Right(Schema(PropertyType.String)))
    else if (typ =:= typeOf[Boolean]) Some(Right(Schema(PropertyType.Boolean)))
    else if (typ <:< typeOf[Map[String, _]]) {
      val elemType = typ.dealias.typeArgs.tail.head
      typeToProperty(elemType).map { schema =>
        Right(Schema(PropertyType.Object, patternProperties = Map(".*" -> schema)))
      }
    }
    else if (typ <:< typeOf[Iterable[_]]) {
      typ.dealias.typeArgs match {
        case elemType :: Nil => typeToProperty(elemType).map { schema =>
          Right(Schema(PropertyType.Array, items = Some(schema)))
        }
        case _ => None
      }
    }
    else if (CustomTypes.allTypes.exists(_._1 =:= typ)) {
      Some(Left(Pointer(CustomTypes.nameOf(typ))))
    }
    else None
  }
}
