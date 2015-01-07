package scavlink.task.client

import akka.actor.{Actor, Props}
import akka.io.IO
import scavlink.ScavlinkContext
import scavlink.task._
import spray.can.Http
import spray.can.server.UHttp
import spray.can.websocket.frame.TextFrame
import spray.can.websocket.{UpgradedToWebSocket, WebSocketClientWorker}
import spray.http.HttpMethods._
import spray.http._
import spray.httpx.RequestBuilding._

object ClientActor {
  def props(sctx: ScavlinkContext, apis: Seq[TaskAPI], settings: ClientSettings, ssl: SslSettings) =
    Props(classOf[ClientActor], sctx, apis, settings, ssl)
}

/**
 * Handles interaction with a remote task service.
 */
class ClientActor(sctx: ScavlinkContext, apis: Seq[TaskAPI], settings: ClientSettings, val sslSettings: SslSettings)
  extends Actor with WebSocketClientWorker with SslConfiguration {

  // save token here after authorization succeeds, since data can't be passed via UpgradedToWebSocket message
  private var token: Option[String] = None
  val maxTokenSize = 32

  val authRequest: (HttpRequest, Http.HostConnectorSetup) = {
    val creds = BasicHttpCredentials(settings.username, settings.password)
    val request = addCredentials(creds)(HttpRequest(GET, "/token"))
    val setup = Http.HostConnectorSetup(settings.host, settings.port, settings.isSecure)
    (request, setup)
  }

  def upgradeRequest: HttpRequest = {
    val headers = List(
      HttpHeaders.Host(settings.host, settings.port),
      HttpHeaders.Connection("Upgrade"),
      HttpHeaders.RawHeader("Upgrade", "websocket"),
      HttpHeaders.RawHeader("Sec-WebSocket-Version", "13"),
      HttpHeaders.RawHeader("Sec-WebSocket-Key", "x3JJHMbDL1EzLkh9GBhXDw=="),
      HttpHeaders.RawHeader("Sec-WebSocket-Extensions", "permessage-deflate"))

    HttpRequest(HttpMethods.GET, s"/task?token=${token.get}", headers)
  }

  def sendAuthorization(): Unit = {
    import context.system
    log.debug("Authorization as user " + settings.username)
    IO(UHttp) ! authRequest
  }

  def connectWebSocket(): Unit = {
    import context.system
    IO(UHttp) ! Http.Connect(settings.host, settings.port, settings.isSecure)
  }


  override def preStart() = sendAuthorization()

  override def receive: Receive = {
    case response: HttpResponse if response.status.isSuccess && response.entity.data.length <= maxTokenSize =>
      token = Some(response.entity.asString)
      log.debug(s"Authorization succeeded: $token")
      connectWebSocket()
      context.become(super.receive)

    case response: HttpResponse =>
      log.error("Authorization call failed: " + response.status + " with length " + response.entity.data.length)

    case x => log.debug(x.toString)
  }


  def businessLogic: Receive = {
    case UpgradedToWebSocket => connection ! TextFrame("scavlink is here")

    case TextFrame(msg) =>
      val string = new String(msg.toArray)
      log.debug(s"Received: $string")
  }
}
