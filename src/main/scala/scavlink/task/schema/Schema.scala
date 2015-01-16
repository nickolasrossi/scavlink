package scavlink.task.schema

import scavlink.link.Vehicle
import scavlink.message.VehicleId
import scavlink.task.{APIMethod, TaskAPI}
import scavlink.util.Memoize

import scala.collection.immutable.ListMap
import scala.reflect.runtime.universe._

/**
 * Represents a single JSON Schema definition.
 * Not all JSON Schema fields are provided here; just the ones we use.
 */
case class Schema(typ: PropertyType.Value,
                  properties: ListMap[String, SchemaDef] = ListMap.empty,
                  required: List[String] = List.empty,
                  patternProperties: Map[String, SchemaDef] = Map.empty,
                  additionalProperties: Option[Boolean] = None,
                  enum: List[String] = List.empty,
                  items: Option[SchemaDef] = None,
                  minItems: Option[Int] = None,
                  maxItems: Option[Int] = None,
                  oneOf: List[SchemaDef] = List.empty,
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
  val Any = Value

  override val values = super.values - Any
}


object Schema {
  /**
   * Generate a schema for the given type, if known.
   */
  val fromKnownType: Type => Option[SchemaDef] = Memoize { typ =>
    if (typ weak_<:< typeOf[Long]) Some(Right(Schema(PropertyType.Integer)))
    else if (typ weak_<:< typeOf[Double]) Some(Right(Schema(PropertyType.Number)))
    else if (typ =:= typeOf[String]) Some(Right(Schema(PropertyType.String)))
    else if (typ =:= typeOf[Boolean]) Some(Right(Schema(PropertyType.Boolean)))
    else if (typ <:< typeOf[Option[_]]) fromKnownType(typ.dealias.typeArgs.head)
    else if (typ <:< typeOf[Map[String, _]]) {
      val elemType = typ.dealias.typeArgs.tail.head
      if (elemType =:= typeOf[Any]) {
        Some(Right(Schema(PropertyType.Object)))
      } else {
        fromKnownType(elemType) map { schema =>
          Right(Schema(PropertyType.Object, patternProperties = Map(".*" -> schema)))
        }
      }
    }
    else if (typ <:< typeOf[Iterable[_]]) {
      typ.dealias.typeArgs match {
        case elemType :: Nil => fromKnownType(elemType) map { schema =>
          Right(Schema(PropertyType.Array, items = Some(schema)))
        }
        case _ => None
      }
    }
    else if (ComplexTypes.types.exists(_._1 =:= typ)) {
      Some(Left(Pointer(definitionNameOf(typ))))
    }
    else None
  }


  /**
   * Construct schema by reflecting the type's constructor.
   */
  def fromClass[T: TypeTag]: Schema = fromClass(typeOf[T])

  def fromClass(typ: Type): Schema = {
    require(typ.typeSymbol.isClass)

    val Some(ctor: MethodSymbol) = typ.members find {
      case m: MethodSymbol if m.isConstructor => true
      case _ => false
    }

    val ctorMethod = APIMethod(ctor)
    val properties = for {
      (name, paramType) <- ctorMethod.params
      schema <- fromKnownType(paramType)
    } yield {
      name -> schema
    }

    val schema = Schema(PropertyType.Object, properties, properties.keys.toList)
    singleProperty(propertyNameOf(typ), schema)
  }

  /**
   * Extract and assemble combined schema for a list of task APIs.
   */
  def fromAPIs(apis: Seq[TaskAPI]): Schema = {
    def _singleProperty(item: (String, Schema)): Schema = singleProperty(item._1, item._2)
    val taskSchemas = apis.foldLeft(ListMap.empty[String, Schema])(_ ++ fromAPI(_))
    Schema(PropertyType.Object, oneOf = taskSchemas.map(_singleProperty).map(Right(_)).toList)
  }

  /**
   * Construct an object schema with a single property of the given name and schema.
   */
  def singleProperty(name: String, schema: Schema): Schema =
    Schema(PropertyType.Object, ListMap(name -> Right(schema)), List(name))

  
  /**
   * Extract schemas for one task API.
   */
  def fromAPI(api: TaskAPI): Map[String, Schema] = {
    val f = fromMethod(api.ctor) _
    api.methods map { case (name, method) =>
      (api.name + "." + name, f(method))
    }
  }

  private[schema] def fromMethod(ctor: APIMethod)(method: APIMethod): Schema = {
    val ctorProperties = methodProperties(ctor)
    val properties = methodProperties(method)
    val required = (ctor.params ++ method.params).filterNot(_._2 <:< typeOf[Option[_]]).keySet --
                   method.defaults.keySet -- ctor.defaults.keySet
    Schema(PropertyType.Object, ctorProperties ++ properties, required.toList)
  }

  private[schema] def methodProperties(method: APIMethod): ListMap[String, SchemaDef] =
    method.params map {
      case (param, typ) if typ =:= typeOf[Vehicle] => (param, fromKnownType(typeOf[VehicleId]))
      case (param, typ) => (param, fromKnownType(typ))
    } collect {
      case (param, Some(prop)) => (param, prop)
    }
}