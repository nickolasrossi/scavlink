package scavlink.connection.udp

import java.net.InetSocketAddress

import akka.actor._
import akka.io.{IO, Udp}
import scavlink.ScavlinkContext

object UdpListener {
  def props(settings: UdpListenerSettings, sctx: ScavlinkContext) = Props(classOf[UdpListener], settings, sctx)
}

/**
 * Listens for MAVLink packets on a UDP port.
 * Packets may come from any remote address, and are fowarded to the corresponding [[UdpReceiver]].
 * A packet received from a previously unknown address will spawn a new [[UdpReceiver]].
 * As this is insecure, it's best to restrict the inferface to a local network, or better yet, localhost.
 * @author Nick Rossi
 */
class UdpListener(settings: UdpListenerSettings, params: ScavlinkContext) extends Actor with ActorLogging {
  val address = new InetSocketAddress(settings.interface, settings.port)

  private var peers = Map[InetSocketAddress, ActorRef]()

  import context.system
  IO(Udp) ! Udp.Bind(self, address)

  def receive = {
    case Udp.Bound(local) =>
      log.debug(s"UDP bound on $local")
      context.become(listen(sender()))
  }

  def listen(socket: ActorRef): Receive = {
    case Udp.Received(data, remote) =>
      peers.get(remote) match {
        case Some(peer) =>
          peer ! data

        case None =>
          val name = ("udp-receiver:" + remote).replace("/", "")
          val peer = context.actorOf(UdpReceiver.props(socket, remote, settings, params), name)
          context.watch(peer)
          peers += remote -> peer
      }

    case Terminated(peer) =>
      peers.find { case (_, v) => v == peer } match {
        case Some((remote, _)) => peers -= remote
        case None => //
      }

    case Udp.Unbind =>
      log.debug(s"UDP unbind from $address")
      socket ! Udp.Unbind

    case Udp.Unbound =>
      log.debug(s"UDP unbound from $address")
      context.stop(self)
  }
}
