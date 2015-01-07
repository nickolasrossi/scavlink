package scavlink.connection.tcp

import scavlink.ScavlinkContext
import scavlink.connection.PacketReceiver
import akka.actor._
import akka.io.Tcp._
import akka.io.{IO, Tcp}

import scala.concurrent.duration.{Duration, FiniteDuration}

object TcpClient {
  def props(settings: TcpClientSettings, sctx: ScavlinkContext): Props = Props(classOf[TcpClient], settings, sctx)
}

class TcpClient(val settings: TcpClientSettings, val sctx: ScavlinkContext) extends PacketReceiver with ActorLogging {
  import context.system
  import context.dispatcher

  val address = settings.actorName

  def doConnect(delay: FiniteDuration) = context.system.scheduler.scheduleOnce(delay, IO(Tcp),
    Connect(settings.server, timeout = Some(settings.timeout)))

  override def preStart() = {
    super.preStart()
    doConnect(Duration.Zero)
  }

  def receive = {
    case CommandFailed(cmd: Connect) =>
      log.debug(s"TCP connection failed to ${cmd.remoteAddress}: ${cmd.failureMessage}")
      doConnect(settings.reconnectInterval)

    case Connected(remote, local) =>
      val socket = sender()
      start(socket ! Write(_), {
        case CommandFailed(w: Write) => log.debug("Failed to write: " + w.data)
      })

      context.become(connected(socket))
      socket ! Register(self)
  }

  def connected(socket: ActorRef): Receive = {
    case Received(data) =>
      receiveData(data)

    case ReceiveTimeout =>
      socket ! Close

    case cl: ConnectionClosed =>
      if (cl.isErrorClosed) log.debug(s"Closed: ${ cl.getErrorCause }")
      context.unbecome()
      super.stop()
      doConnect(Duration.Zero)
  }
}
