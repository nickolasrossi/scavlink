import java.net.InetSocketAddress

import akka.actor.{ActorSystem, Props}
import scavlink.ScavlinkInstance
import scavlink.connection.serial.{Serial, SerialClientSettings}
import scavlink.connection.tcp.TcpClientSettings
import scavlink.connection.udp.UdpListenerSettings
import scavlink.test.PacketPrinter

import scala.concurrent.duration._

/**
 * Run the library without a map view.
 */
object Headless extends App {
  val system = ActorSystem("TcpClient")
  val scavlink = ScavlinkInstance(system)
//  val test = system.actorOf(AdHocTestActor.props(scavlink.events), "test")
  val test = system.actorOf(Props(classOf[PacketPrinter]), "printer")
  scavlink.events.subscribeToAll(test)

  system.awaitTermination()


  def startTcpClient(addr: String, port: Int) =
    scavlink.startConnection(TcpClientSettings(new InetSocketAddress(addr, port), 10.seconds, 10.seconds))

  def startUdpListener(interface: String, port: Int) =
    scavlink.startConnection(UdpListenerSettings(interface, port))

  def startSerialPort(addr: String) =
    scavlink.startConnection(SerialClientSettings(addr, Serial.DefaultOptions, 2.seconds, 10.seconds))
}
