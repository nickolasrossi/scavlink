package scavlink.task.service

import akka.actor._
import akka.io.IO
import scavlink.{GetVehicles, ScavlinkContext}
import scavlink.connection.{ConnectionSubscribeTo, Vehicles}
import scavlink.link.Vehicle
import scavlink.message.VehicleId
import scavlink.task._
import scavlink.task.schema.TaskSchema
import spray.can.server.UHttp
import spray.can.{Http, websocket}
import spray.http.HttpHeaders.{Authorization, Connection}
import spray.http.{BasicHttpCredentials, HttpResponse, OAuth2BearerToken, StatusCodes}
import spray.httpx.Json4sJacksonSupport
import spray.routing.authentication.{BasicAuth, UserPass, UserPassAuthenticator}
import spray.routing.directives.{CachingDirectives, ExecutionDirectives}
import spray.routing.{HttpServiceActor, Route, RoutingSettings}
import spray.util._

import scala.util.Success

object TaskServiceActor {
  def props(rootSupervisor: ActorRef, sctx: ScavlinkContext, apis: Seq[TaskAPI],
            settings: ServiceSettings, ssl: SslSettings): Props =
    Props(classOf[TaskServiceActor], rootSupervisor, sctx, apis, settings, ssl)
}

/**
 * Http service endpoint that can be upgraded to a WebSocket.
 *
 * GET|POST /token => authenticate user and receive token (Basic or OAuth2)
 * GET /schema => return JSON Schema of all available tasks
 * GET /vehicles => return list of active vehicles
 *
 * @author Nick Rossi
 */
class TaskServiceActor(rootSupervisor: ActorRef,
                       sctx: ScavlinkContext, 
                       apis: Seq[TaskAPI],
                       settings: ServiceSettings,
                       val sslSettings: SslSettings)
  extends HttpServiceActor with ActorLogging with SslConfiguration
  with CachingDirectives with ExecutionDirectives with Json4sJacksonSupport {

  import context.dispatcher
  implicit val json4sJacksonFormats = Serializers.all
  implicit val routeSettings = RoutingSettings(context.system.settings.config)

  // the current list of active vehicles (kept up to date by event subscription)
  private var vehicles: Map[VehicleId, Vehicle] = Map.empty
  private val tokenStore = new MemoryTokenStore(settings.tokenIdleTimeout)
  private val httpCache = routeCache()


  override def preStart() = {
    sctx.events.subscribe(self, ConnectionSubscribeTo.event(classOf[Vehicles]))
    rootSupervisor ! GetVehicles
    IO(UHttp)(context.system) ! Http.Bind(self, settings.interface, settings.port)
  }

  def receive: Receive = authHandshaking orElse runRoute(plainHttp)

  def authHandshaking: Receive = {
    case websocket.HandshakeRequest(state) =>
      val origin = sender()

      state match {
        case fail: websocket.HandshakeFailure =>
          origin ! fail.response

        case ctx: websocket.HandshakeContext =>
          // check for valid token before upgrading connection
          val authHeader = ctx.request.headers.findByType[`Authorization`]
          val credentials = authHeader.map { case Authorization(creds) => creds }
          val token = credentials match {
            case Some(BasicHttpCredentials(u, pw)) => Some(pw)
            case Some(OAuth2BearerToken(t)) => Some(t)
            case _ => ctx.request.uri.query.get("token")
          }

          tokenStore.checkString(token) onComplete {
            case Success(Some(user)) =>
              val worker = context.actorOf(TaskRequestActor.props(origin, rootSupervisor, sctx, apis, vehicles, user))
              origin ! UHttp.UpgradeServer(websocket.pipelineStage(worker, ctx), ctx.response)

            case _ =>
              origin ! websocket.HandshakeFailure(ctx.request, ctx.protocal, ctx.extensions).response
          }

        case _ =>
          origin ! HttpResponse(status = StatusCodes.Unauthorized)
      }


    case Vehicles(vs) =>
      log.debug("Vehicle list updated")
      vehicles = vs

    case Terminated(actor) =>
      log.debug(s"Shutdown of request actor $actor")
  }


  // route for non-websocket requests
  val plainHttp: Route =
    jsonpWithParameter("jsonp") {
      path("token") {
        respondWithHeader(Connection("close")) {
          (post & parameters('grant_type ! "password", 'username, 'password)) { (user, pass) =>   // OAuth2
            val authenticator = UserPassAuthenticator.fromConfig[Token](routeSettings.users)(tokenStore.addUserPass)
            complete {
              authenticator(Some(UserPass(user, pass)))
            }
          } ~
          (get & authenticate(BasicAuth("user", tokenStore.addUserPass _))) { token =>
            complete(token)
          }
        }
      } ~
      authenticate(BasicAuth(tokenStore.checkUserPass _, "token")) { user =>
        get {
          path("schema") {
            alwaysCache(httpCache) {
              complete(TaskSchema(apis))
            }
          } ~
          path("vehicles") {
            complete(vehicles.values)
          }
        }
      }
    }
}
