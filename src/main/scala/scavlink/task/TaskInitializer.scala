package scavlink.task

import akka.actor.{ActorRef, ActorRefFactory}
import scavlink.task.client.ClientActor
import scavlink.task.service.TaskServiceActor
import scavlink.{ScavlinkContext, ScavlinkInitializer}

import scala.util.{Success, Try}

/**
 * Initializes service and clients (if enabled) when the library starts up.
 */
object TaskInitializer extends ScavlinkInitializer {

  def apply(sup: ActorRef, sctx: ScavlinkContext, actx: ActorRefFactory): Seq[ActorRef] = {
    val settings = TaskSettings(sctx.config.root)
    val apis = settings.apis.map(cls => Try(TaskAPI(cls))).collect {
      case Success(api) => api
    }

    val builder = List.newBuilder[ActorRef]

    if (apis.nonEmpty) {
      if (settings.service.isEnabled) {
        builder += actx.actorOf(TaskServiceActor.props(sup, sctx, apis, settings.service, settings.ssl), "service")
      }

      settings.clients collect {
        case client if client.isEnabled =>
          builder += actx.actorOf(ClientActor.props(sctx, apis, client, settings.ssl), actorName(client))
      }
    }

    builder.result()
  }

  def actorName(settings: ClientSettings): String = {
    val sb = new StringBuilder
    if (settings.isSecure) sb.append("wss") else sb.append("ws")
    sb.append(":") // slashes aren't allowed
      .append(settings.username)
      .append("@")
      .append(settings.host)
      .append(":")
      .append(settings.port)

    sb.result()
  }
}
