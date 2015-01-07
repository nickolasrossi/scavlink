package scavlink.connection.udp

import java.net.InetSocketAddress

import akka.actor.{ActorLogging, Actor, ActorRef, Props}
import akka.io.{IO, Udp}
import akka.util.ByteString

object UdpForwarder {
  def props(remote: InetSocketAddress): Props = Props(classOf[UdpForwarder], remote)
}

/**
 * Simple forwarder that merely sends vehicle data to an external GCS.
 * The GCS will only be able to observe vehicle data, and not send anything back to the vehicle.
 */
class UdpForwarder(remote: InetSocketAddress) extends Actor with ActorLogging {
  import context.system
  IO(Udp) ! Udp.SimpleSender
  
  def receive = {
    case Udp.SimpleSenderReady =>
      log.debug(s"Initializing forward-only to $remote")
      context.become(ready(sender()))
  }
  
  def ready(send: ActorRef): Receive = {
    case data: ByteString => send ! Udp.Send(data, remote)
  }
}
