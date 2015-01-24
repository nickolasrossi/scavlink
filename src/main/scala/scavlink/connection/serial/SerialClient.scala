package scavlink.connection.serial

import scavlink.ScavlinkContext
import scavlink.connection.PacketReceiver
import akka.actor.{Props, ReceiveTimeout, ActorRef, ActorLogging}
import akka.io.IO
import Serial._

import scala.concurrent.duration.{Duration, FiniteDuration}

object SerialClient {
  def props(settings: SerialClientSettings, sctx: ScavlinkContext) = Props(classOf[SerialClient], settings, sctx)
}

class SerialClient(val settings: SerialClientSettings, val sctx: ScavlinkContext) extends PacketReceiver with ActorLogging {
  import context.system
  import context.dispatcher

  val address = settings.actorName

  def doConnect(delay: FiniteDuration) =
    context.system.scheduler.scheduleOnce(delay, IO(Serial),
      Open(settings.address, settings.options, settings.connectTimeout))

  override def preStart() = {
    super.preStart()
    doConnect(Duration.Zero)
  }

  def receive: Receive = {
    case CommandFailed(cmd: Open, error) =>
      log.warning(s"Connection failed to ${cmd.port}: $error")
      doConnect(settings.reconnectInterval)

    case Opened(addr, _) =>
      log.info(s"Opened $addr")

      val port = sender()
      start(port ! Write(_), {
        case CommandFailed(w: Write, error) => log.debug(s"Failed to write: $error")
      })

      context.become(connected(port))
      port ! Register(self)
  }

  def connected(port: ActorRef): Receive = {
    case Received(data) =>
      receiveData(data)

    case ReceiveTimeout =>
      port ! Close

    case _: PortClosed =>
      context.unbecome()
      super.stop()
      doConnect(Duration.Zero)
  }
}
