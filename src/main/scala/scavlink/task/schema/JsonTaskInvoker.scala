package scavlink.task.schema

import akka.actor.ActorRef
import com.fasterxml.jackson.core.JsonParseException
import org.json4s.JsonAST.{JField, JObject}
import org.json4s._
import org.json4s.jackson.JsonMethods._
import scavlink.coord.{Geo, LatLon}
import scavlink.link.Vehicle
import scavlink.link.operation.{OpFlags, WithContext, WithProgress}
import scavlink.message.common.MissionItem
import scavlink.message.{Mode, VehicleId}
import scavlink.task.Serializers._
import scavlink.task._

import scala.reflect.runtime.universe._

class InvalidJsonException(msg: String) extends Exception(msg)

/**
 * Unmarshals a JSON document and translates it to a method call on an API class.
 *
 * Handling the unmarshalling here allows us to parse values based on the expected
 * paramter types of the requested method.
 *
 * @param apis API objects
 * @param vehicles currently known vehicles
 * @author Nick Rossi
 */
class JsonTaskInvoker(apis: Seq[TaskAPI], vehicles: Map[VehicleId, Vehicle], sender: ActorRef) {

  import org.json4s.DefaultReaders._

  implicit val formats = Serializers.all

  implicit object MissionItemReader extends Reader[MissionItem] {
    def read(value: JValue): MissionItem = MissionItemSerializer.deserialize(formats)(TypeInfo(classOf[MissionItem], None), value)
  }

  implicit object GeoReader extends Reader[Geo] {
    def read(value: JValue): Geo = GeoSerializer.deserialize(formats)(TypeInfo(classOf[Geo], None), value)
  }

  implicit object LatLonReader extends Reader[LatLon] {
    def read(value: JValue): LatLon = LatLonSerializer.deserialize(formats)(TypeInfo(classOf[LatLon], None), value)
  }

  implicit object ModeReader extends Reader[Mode] {
    def read(value: JValue): Mode = ModeSerializer.deserialize(formats)(TypeInfo(classOf[Mode], None), value)
  }

  implicit object VehicleIdReader extends Reader[VehicleId] {
    def read(value: JValue): VehicleId = VehicleIdSerializer.deserialize(formats)(TypeInfo(classOf[VehicleId], None), value)
  }


  @throws[MethodNotFoundException]
  @throws[ParameterMissingException]
  @throws[InvalidJsonException]
  def invokeJsonString(json: String, ctx: Any = None): Any = {
    require(json.length <= 1024 * 1024)
    try {
      invokeJson(parse(json), ctx)
    } catch {
      case e: JsonParseException => throw new InvalidJsonException(e.getMessage)
    }
  }

  /**
   * Invoke an API method from the encoding in a json object:
   * { "API.method": { "param1": value1, "param2": value2, ... } }
   * @param json json formatted string
   * @return return value of invoked method
   */
  @throws[MethodNotFoundException]
  @throws[ParameterMissingException]
  @throws[InvalidJsonException]
  def invokeJson(json: JValue, ctx: Any = None): Any = {
    val (api, method, args) = parseJson(json, ctx)
    api.invokeMethod(method, args)
  }

  /**
   * Extract invocation from json object.
   */
  @throws[MethodNotFoundException]
  @throws[ParameterMissingException]
  @throws[InvalidJsonException]
  def parseJson(json: JValue, ctx: Any = None): (TaskAPI, APIMethod, Map[String, Any]) = json match {
    case JObject(JField(name, jvals: JObject) :: Nil) =>
      val parts = name.split('.')
      val (apiName, methodName) = if (parts.length > 1) {
        (parts(0), parts(1))
      } else {
        throw new MethodNotFoundException(name)
      }

      val invoker = for {
        api <- apis.find(_.name == apiName)
        method <- api.methods.get(methodName)
      } yield {
        val params = api.ctor.params ++ method.params
        var args = parseParams(jvals, params)

        // add special parameter values by type
        params foreach {
          case (p, t) if t =:= typeOf[ActorRef] =>
            args += p -> sender

          case (p, t) if t =:= typeOf[OpFlags] =>
            args += p -> (WithProgress | WithContext(ctx))

          case _ => //
        }

        (api, method, args)
      }

      invoker match {
        case Some(x) => x
        case None => throw new MethodNotFoundException(s"Unknown method '$methodName'")
      }

    case _ => throw new InvalidJsonException("json did not contain exactly one object field")
  }

  /**
   * Parse values from json object using the expected parameter types.
   */
  def parseParams(json: JObject, params: Map[String, Type]): Map[String, Any] = {
    val parsed = for {
      (p, v) <- json.obj
      t <- params.get(p)
      nv <- parseValue(t)(v)
    } yield {
      (p, nv)
    }

    parsed.toMap
  }

  /**
   * Boilerplate to convert typed values.
   * Need to explore whether shapeless or other library can support a different design here.
   */
  def parseValue(expectType: Type)(value: JValue): Option[Any] = expectType match {
    case t if t =:= typeOf[String] => value.getAs[String]
    case t if t =:= typeOf[Boolean] => value.getAs[Boolean]
    case t if t =:= typeOf[Double] => value.getAs[Double]
    case t if t =:= typeOf[Float] => value.getAs[Float]
    case t if t =:= typeOf[Long] => value.getAs[Long]
    case t if t =:= typeOf[Int] => value.getAs[Int]
    case t if t =:= typeOf[Short] => value.getAs[Short]
    case t if t =:= typeOf[Byte] => value.getAs[Byte]

    case t if t =:= typeOf[VehicleId] => value.getAs[VehicleId]
    case t if t =:= typeOf[Vehicle] => (value.getAs[VehicleId] map vehicles.get).flatten
    case t if t =:= typeOf[Geo] => value.getAs[Geo]
    case t if t =:= typeOf[LatLon] => value.getAs[LatLon]
    case t if t =:= typeOf[MissionItem] => value.getAs[MissionItem]
    case t if t =:= typeOf[Mode] => value.getAs[Mode]

    case t if t <:< typeOf[Option[_]] =>
      val elemType = t.dealias.typeArgs.head
      val arg = parseValue(elemType)(value)
      arg.map(Some(_))  // re-box as option

    case t if t <:< typeOf[Map[String, _]] => value match {
      case JObject(values) =>
        val elemType = t.dealias.typeArgs.tail.head
        if (values.isEmpty) return Some(Map.empty)
        val items = for ((k, v) <- values; nv <- parseValue(elemType)(v)) yield (k, nv)
        if (items.nonEmpty) Some(items.toMap) else None // return None for unparseable values

      case _ => None
    }

    case t if t <:< typeOf[Iterable[_]] => value match {
      case JArray(array) =>
        val elemType = t.dealias.typeArgs.head
        val items = for (v <- array; nv <- parseValue(elemType)(v)) yield nv

        t match {
          case x if x <:< typeOf[List[_]] => Some(items)
          case x if x <:< typeOf[Vector[_]] => Some(items.toVector)
          case x if x <:< typeOf[Set[_]] => Some(items.toSet)
          case _ => None
        }

      case _ => None
    }

    case _ => None
  }
}
