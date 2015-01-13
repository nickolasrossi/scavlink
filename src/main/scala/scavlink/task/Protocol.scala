package scavlink.task

import java.security.SecureRandom

import org.json4s.JsonDSL.WithDouble._
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.parboiled.common.Base64
import scavlink.coord.Geo
import scavlink.link.Vehicle
import scavlink.message.{Mode, VehicleId}
import scavlink.message.enums.MavState
import scavlink.state.GpsFixType
import scavlink.task.schema.InvalidJsonException
import scavlink.util.Memoize


object RequestContext {
  private val random = new SecureRandom
  private val base64 = Base64.custom()

  def empty = ""

  def generate(): RequestContext = {
    val bytes = new Array[Byte](24)
    random.nextBytes(bytes)
    base64.encodeToString(bytes, false)
  }
}


/**
 * All request messages for the WebSocket protocol.
 */
sealed trait Request
case class StartTask(method: JValue) extends Request {
  require(method != JNothing)
}
case class StopTask(context: RequestContext) extends Request {
  require(context != JNothing, "Task context to stop not provided")
}
case class StartTelemetry(vehicle: VehicleId, interval: Int) extends Request {
  require(interval > 0 && interval <= 60, "telemetry interval must be between 1 and 60 seconds")
}
case class StopTelemetry(vehicle: VehicleId) extends Request

/**
 * All response messages for the WebSocket protocol.
 */
sealed trait Response
case class VehicleUp(vehicle: Vehicle) extends Response
case class VehicleDown(vehicle: Vehicle) extends Response
case class StatusMessage(vehicle: VehicleId, text: String) extends Response
case class Error(message: String) extends Response
case class Telemetry(vehicle: VehicleId, location: Geo = Geo(), batteryVoltage: Double = 0,
                     state: MavState.Value = MavState(0), mode: Mode = Mode.Unknown,
                     throttle: Double = 0, course: Double = 0, heading: Double = 0, groundspeed: Double = 0,
                     climb: Double  = 0, gpsFix: GpsFixType.Value = GpsFixType(0)) extends Response

sealed trait TaskStatus extends Response {
  def message: String
  def data: Map[String, Any]
}

case class TaskProgress(progress: Int = 0, message: String = "", data: Map[String, Any] = Map.empty)
  extends TaskStatus

case class TaskComplete(success: Boolean = true, message: String = "", data: Map[String, Any] = Map.empty)
  extends TaskStatus

case class ContextResponse(context: RequestContext, obj: Response)

object TaskComplete {
  def apply(data: Map[String, Any]): TaskComplete = TaskComplete(true, "", data)
  def failed(): TaskComplete = TaskComplete(false)
  def failed(error: String): TaskComplete = TaskComplete(false, error)
}


/**
 * JSON conversions of request and response objects for our WebSocket protocol.
 * @author Nick Rossi
 */
object Protocol {

  import org.json4s.Formats._

  val MaxJsonSize = 1024 * 1024
  val ContextField = "context"
  implicit val formats = Serializers.all

  val fieldNameFor = Memoize[Class[_], String] { cls =>
    val field = cls.getSimpleName
    field.head.toLower + field.tail
  }


  implicit object StartTaskReader extends Reader[StartTask] {
    def read(json: JValue): StartTask = StartTask(json)
  }

  implicit object StopTaskReader extends Reader[StopTask] {
    def read(value: JValue): StopTask = {
      val context = value \ "context"
      if (context == JNothing) throw new MappingException(s"Task context to stop not provided")
      StopTask(context)
    }
  }

  implicit object StartTelemetryReader extends Reader[StartTelemetry] {
    def read(value: JValue): StartTelemetry = value match {
      case JString(v) => StartTelemetry(VehicleId(v), 1)
      case jobj: JObject =>
        val vehicle = jobj \ "vehicle"
        val interval = jobj \ "interval"
        (vehicle, interval) match {
          case (JString(v), JInt(i)) => StartTelemetry(VehicleId(v), i.toInt)
          case (JString(v), JNothing) => StartTelemetry(VehicleId(v), 1)
          case x => throw new MappingException(s"Can't convert $x to StartTelemetry")
        }

      case x => throw new MappingException(s"Can't convert $x to StartTelemetry")
    }
  }

  implicit object StopTelemetryReader extends Reader[StopTelemetry] {
    def read(value: JValue): StopTelemetry = value match {
      case JString(v) => StopTelemetry(VehicleId(v))
      case jobj: JObject =>
        jobj \ "vehicle" match {
          case JString(v) => StopTelemetry(VehicleId(v))
          case x => throw new MappingException(s"Can't convert $x to StartTelemetry")
        }

      case x => throw new MappingException(s"Can't convert $x to StopTelemetry")
    }
  }


  implicit object VehicleUpWriter extends Writer[VehicleUp] {
    def write(obj: VehicleUp): JValue = Extraction.decompose(obj)
  }

  implicit object VehicleDownWriter extends Writer[VehicleDown] {
    def write(obj: VehicleDown): JValue = Extraction.decompose(obj)
  }

  class FieldWriter[A: Manifest] extends Writer[A] {
    val _formats = formats + new FieldSerializer[A]
    def write(obj: A): JValue = Extraction.decompose(obj)(_formats)
  }

  implicit object TaskProgressWriter extends FieldWriter[TaskProgress]
  implicit object TaskCompleteWriter extends FieldWriter[TaskComplete]
  implicit object StatusMessageWriter extends FieldWriter[StatusMessage]
  implicit object TelemetryWriter extends FieldWriter[Telemetry]

  implicit object ErrorWriter extends Writer[Error] {
    def write(obj: Error): JValue = JString(obj.message)
  }


  def parseJson(in: String): (RequestContext, JObject) = {
    require(in.length <= MaxJsonSize, "JSON too large")
    parse(in) match {
      case json: JObject =>
        var ctx = json \ ContextField
        if (ctx == JNothing) {
          ctx = RequestContext.generate()
        }

        (ctx, JObject(json.obj.filter(_._1 != ContextField)))

      case _ => throw new InvalidJsonException("JSON object required")
    }
  }

  def readRequest(ctx: RequestContext, json: JObject): Request = {
    json.obj match {
      case JField(req, obj: JValue) :: Nil =>
        req match {
          case r if r == fieldNameFor(classOf[StartTask]) => read[StartTask](obj)
          case r if r == fieldNameFor(classOf[StopTask]) => read[StopTask](obj)
          case r if r == fieldNameFor(classOf[StartTelemetry]) => read[StartTelemetry](obj)
          case r if r == fieldNameFor(classOf[StopTelemetry]) => read[StopTelemetry](obj)
          case r => throw new InvalidJsonException(s"Unrecognized request '$r'")
        }

      case Nil => throw new InvalidJsonException("Empty request")
      case _ => throw new InvalidJsonException("Only one request allowed")
    }
  }

  def writeResponse(response: Response, context: RequestContext = JNothing): String = {
    val field = fieldNameFor(response.getClass)
    val value = response match {
      case o: VehicleUp => write(o)
      case o: VehicleDown => write(o)
      case o: TaskProgress => write(o)
      case o: TaskComplete => write(o)
      case o: StatusMessage => write(o)
      case o: Telemetry => write(o)
      case o: Error => write(o)
    }

    val json: JObject = (field -> value) ~ ("context" -> context)
    compact(json)
  }
}
