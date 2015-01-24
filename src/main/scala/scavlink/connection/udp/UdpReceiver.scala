package scavlink.connection.udp

import java.net.InetSocketAddress

import akka.actor._
import akka.io.Udp
import akka.util.ByteString
import scavlink.ScavlinkContext
import scavlink.connection._

object UdpReceiver {
  def props(socket: ActorRef, remote: InetSocketAddress, settings: UdpListenerSettings, sctx: ScavlinkContext) =
    Props(classOf[UdpReceiver], socket, remote, settings, sctx)
}

/**
 * Receives UDP packets for a single vehicle connection.
 */
class UdpReceiver(socket: ActorRef, remote: InetSocketAddress, val settings: UdpListenerSettings, val sctx: ScavlinkContext)
  extends Actor with ActorLogging with PacketReceiver {
  val address = self.path.name

  override def preStart() = {
    super.preStart()

    start(
      socket ! Udp.Send(_, remote),
      { case Udp.CommandFailed(cmd) => log.debug(s"UDP send failure: $cmd") }
    )

    log.info(s"UDPReceiver started for $remote")
  }

  def receive = {
    case data: ByteString =>
      receiveData(data)

    case ReceiveTimeout =>
      context.stop(self)
      log.info(s"UDPReceiver stopped for $remote")
  }
}
