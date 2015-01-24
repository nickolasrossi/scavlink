package scavlink.task

import akka.actor.{Actor, ActorLogging, ActorRef}
import scavlink.connection.{ConnectionSubscribeTo, Vehicles}
import scavlink.link.Vehicle
import scavlink.link.operation.CancelContext
import scavlink.message.VehicleId
import scavlink.task.Protocol._
import scavlink.task.schema.{InvalidJsonException, JsonTaskInvoker}
import scavlink.{GetVehicles, ScavlinkContext}
import spray.can.websocket.UpgradedToWebSocket
import spray.can.websocket.frame.TextFrame
import spray.routing.authentication.BasicUserContext

import scala.annotation.tailrec

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
trait TaskSession extends TaskSessionTelemetry {
  _: Actor with ActorLogging =>

  def rootSupervisor: ActorRef
  def sctx: ScavlinkContext
  def apis: Seq[TaskAPI]
  def initVehicles: Map[VehicleId, Vehicle]
  def user: BasicUserContext
  implicit val formats = Serializers.all

  private var vehicles: Map[VehicleId, Vehicle] = Map.empty
  private var tasks: Map[RequestContext, (Expect, ActorRef)] = Map.empty
  private var invoker = newInvoker()
  private def newInvoker() = new JsonTaskInvoker(apis, vehicles, self)

  sctx.events.subscribe(self, ConnectionSubscribeTo.event(classOf[Vehicles]))
  updateVehicles(initVehicles)

  /**
   * Send text to the other end of the connection.
   */
  def send(output: String): Unit

  /**
   * Generate an error message from any given exception.
   */
  def errorMessage(e: Throwable): String = {
    @tailrec def cause(e: Throwable): Throwable = if (e.getCause == null) e else cause(e.getCause)
    val ec = cause(e)
    if (ec.getMessage != null) ec.getMessage else e.getClass.getSimpleName
  }


  // actor receive handler

  def taskSession: Receive = taskHandler orElse receiveTelemetry

  private def taskHandler: Receive = {
    case UpgradedToWebSocket => rootSupervisor ! GetVehicles // refresh in case of changes during upgrade

    case Vehicles(vs) => updateVehicles(vs)

    case TextFrame(msg) => handleRequest(new String(msg.toArray))

    case (ctx: RequestContext, obj: Any) => handleUpdate(ctx, obj)
  }


  // handlers

  def handleRequest(raw: String): Unit = {
    val (ctx, json) = try {
      parseJson(raw)
    } catch {
      case e: Exception =>
        send(writeResponse(Error(errorMessage(e))))
        return
    }

    try {
      val request = readRequest(ctx, json)
      log.debug(s"Request: $request with context $ctx")

      request match {
        case req: StartTask => startTask(ctx, req)
        case req: StopTask => stopTask(ctx, req)
        case req: StartTelemetry => startTelemetry(vehicles(req.vehicle), req.interval)
        case req: StopTelemetry => stopTelemetry(vehicles(req.vehicle))
        case req => throw new InvalidJsonException(s"Unrecognized request: $req")
      }
    } catch {
      case e: Exception =>
        send(writeResponse(Error(errorMessage(e)), ctx))
    }
  }

  def handleUpdate(ctx: RequestContext, update: Any): Unit = {
    log.debug(s"Update: ($ctx, $update)")

    val status: TaskStatus = update match {
      case obj: TaskStatus => obj

      case obj => tasks.get(ctx) match {
        case Some((expect, origin)) =>
          tasks += ctx ->(expect, sender()) // update origin actor

          if (expect.progress.isDefinedAt(obj)) {
            expect.progress(obj)
          } else if (expect.failure.isDefinedAt(obj)) {
            expect.failure(obj)
          } else if (expect.result.isDefinedAt(obj)) {
            expect.result(obj)
          } else {
            TaskProgress()
          }

        case None => TaskProgress()
      }
    }

    if (status.isInstanceOf[TaskComplete]) {
      tasks -= ctx
    }

    send(writeResponse(status, ctx))
  }

  def updateVehicles(vs: Map[VehicleId, Vehicle]): Unit = {
    val newIds = vs.keySet
    val oldIds = vehicles.keySet

    newIds diff oldIds foreach { id =>
      val vehicle = vs(id)
      send(writeResponse(VehicleUp(vehicle)))
    }

    oldIds diff newIds foreach { id =>
      val vehicle = vehicles(id)
      send(writeResponse(VehicleDown(vehicle)))
      stopTelemetry(vehicle)
    }

    vehicles = vs
    invoker = newInvoker()
  }


  // protocol requests

  def startTask(ctx: RequestContext, request: StartTask): Unit = {
    val response = try {
      invoker.invokeJson(request.method, ctx) match {
        case expect: Expect =>
          tasks += ctx ->(expect, Actor.noSender) // actor will be updated when first status received
          TaskProgress(-1, "submitted")

        case _: Unit =>
          tasks += ctx ->(ExpectOpDefault(), Actor.noSender)
          TaskProgress(-1, "submitted")

        case x =>
          TaskComplete(Map("result" -> x))
      }
    } catch {
      case e: Exception =>
        TaskComplete.failed(errorMessage(e))
    }

    send(writeResponse(response, ctx))
  }

  def stopTask(ctx: RequestContext, request: StopTask): Unit = {
    tasks.get(request.context) match {
      case Some((expect, origin)) if origin != Actor.noSender =>
        log.debug(s"Canceling ${ request.context }")
        origin ! CancelContext(request.context)

      case _ => //
    }
  }
}
