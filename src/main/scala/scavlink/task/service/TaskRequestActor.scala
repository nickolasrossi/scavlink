package scavlink.task.service

import akka.actor._
import scavlink.ScavlinkContext
import scavlink.link.Vehicle
import scavlink.message.VehicleId
import scavlink.task._
import spray.can.websocket.WebSocketServerWorker
import spray.can.websocket.frame.TextFrame
import spray.routing.HttpServiceActor
import spray.routing.authentication.BasicUserContext

object TaskRequestActor {
  def props(serverConnection: ActorRef, rootSupervisor: ActorRef, sctx: ScavlinkContext,
            apis: Seq[TaskAPI], vehicles: Map[VehicleId, Vehicle], user: BasicUserContext) =
    Props(classOf[TaskRequestActor], serverConnection, rootSupervisor, sctx, apis, vehicles, user)
}

/**
 * Handles a single http request.
 * It's a separate actor because it may upgrade to a websocket and become a task worker.
 * Otherwise, it responds with the plain http route.
 * @author Nick Rossi
 */
class TaskRequestActor(val serverConnection: ActorRef,
                       val rootSupervisor: ActorRef,
                       val sctx: ScavlinkContext,
                       val apis: Seq[TaskAPI],
                       val initVehicles: Map[VehicleId, Vehicle],
                       val user: BasicUserContext)
  extends HttpServiceActor with WebSocketServerWorker with TaskSession with ActorLogging {

  def send(output: String): Unit = send(TextFrame(output))

  def businessLogic: Receive = taskSession
}
