package scavlink.task

import akka.actor.{Actor, ActorLogging, ActorRef}
import org.json4s.Extraction
import org.json4s.JsonAST.JString
import org.json4s.jackson.JsonMethods._
import scavlink.connection.{ConnectionSubscribeTo, VehicleDown, VehicleUp, Vehicles}
import scavlink.link.Vehicle
import scavlink.message.VehicleId
import scavlink.task.TaskInvokerSession.Response
import scavlink.task.schema.JsonTaskInvoker
import scavlink.{GetVehicles, ScavlinkContext}
import spray.can.websocket.UpgradedToWebSocket
import spray.can.websocket.frame.TextFrame
import spray.routing.authentication.BasicUserContext

/**
 * Task invoker for a single user's websocket connection.
 * Multiple tasks may be invoked; progress notifications are pushed when available.
 *
 * However, since there isn't currently a persistence mechanism in the service,
 * only tasks invoked by the current user are reported here.
 *
 * If the user closes the connection while tasks are in progress, those tasks will be aborted.
 *
 * @author Nick Rossi
 */
trait TaskInvokerSession {
  _: Actor with ActorLogging =>

  sctx.events.subscribe(context.self, ConnectionSubscribeTo.event(classOf[Vehicles]))

  def supervisor: ActorRef
  def sctx: ScavlinkContext
  def apis: Seq[TaskAPI]
  def initVehicles: Map[VehicleId, Vehicle]
  def user: BasicUserContext
  implicit val formats = Serializers.all

  private var vehicles: Map[VehicleId, Vehicle] = initVehicles
  private var tasks: Map[TaskId, Expect] = Map.empty
  private var invoker = newInvoker()
  private def newInvoker() = new JsonTaskInvoker(apis, vehicles, self)

  def send(output: String): Unit

  def startTask(raw: String): TaskStatus = {
    val (id, jobj) = try {
      require(raw.length <= 1024 * 1024, "JSON too large")
      val json = parse(raw)
      println(json)

      val id = json \ "id" match {
        case JString(s) => s
        case _ => TaskId.generate()
      }

      val methodOnly = json.removeField(_._1 == "id")
      (id, methodOnly)
    } catch {
      case e: Exception =>
        return TaskComplete.failed(TaskId.empty, e.getMessage)
    }

    println(jobj)

    try {
      invoker.invokeJson(jobj, id) match {
        case expect: Expect =>
          tasks += id -> expect
          TaskProgress(id, -1, "submitted")

        case _: Unit =>
          tasks += id -> ExpectOpDefault()
          TaskProgress(id, -1, "submitted")

        case x =>
          TaskComplete(id, Map("value" -> x))
      }
    } catch {
      case e: Exception =>
        def cause(e: Throwable): Throwable = if (e.getCause == null) e else cause(e.getCause)
        val message = cause(e).getMessage
        TaskComplete.failed(id, message)
    }
  }

  def handleResponse(id: TaskId, obj: Any): Boolean = {
    log.debug(s"Received ($id, $obj)")
    val status: TaskStatus = obj match {
      case o: TaskStatus => o

      case o =>
        tasks.get(id) match {
          case Some(expect) =>
            val opMessage = (id, o)

            if (expect.progress.isDefinedAt(opMessage)) {
              expect.progress(opMessage)
            } else if (expect.failure.isDefinedAt(opMessage)) {
              expect.failure(opMessage)
            } else if (expect.result.isDefinedAt(opMessage)) {
              expect.result(opMessage)
            } else {
              TaskProgress(id)
            }

          case None => TaskProgress(id)
        }
    }

    handleStatus(status)
  }

  def handleStatus(status: TaskStatus): Boolean = {
    send(response(status))

    val isComplete = status.isInstanceOf[TaskComplete]
    if (isComplete) tasks -= status.id
    isComplete
  }


  def taskWorker: Receive = {
    case UpgradedToWebSocket =>
      vehicles.foreach { case (id, v) =>
        send(response(VehicleUp(v)))
      }

      supervisor ! GetVehicles // refresh in case of changes during upgrade

    case Vehicles(vs) =>
      val vsIds = vs.keySet
      val vehicleIds = vehicles.keySet
      vsIds diff vehicleIds foreach { id =>
        send(response(VehicleUp(vs(id))))
      }
      vehicleIds diff vsIds foreach { id =>
        send(response(VehicleDown(vehicles(id))))
      }

      vehicles = vs
      invoker = newInvoker()

    case TextFrame(msg) =>
      val json = new String(msg.toArray)
      val initialResponse = startTask(json)
      handleStatus(initialResponse)

    case (id: TaskId, obj: Any) => handleResponse(id, obj)

    case status: TaskStatus => handleStatus(status)
  }


  def response(obj: Any, name: String = ""): String = compact(Extraction.decompose(Response(obj, name)))
}


object TaskInvokerSession {
  case class Response(obj: Any, name: String = "")
}
