package scavlink.connection.serial

import akka.actor._
import akka.io.IO
import akka.util.ByteString

import scala.concurrent.duration.FiniteDuration

/**
 * Serial port extension for Akka IO, patterned after the Tcp implementation.
 */
object Serial extends ExtensionId[SerialExt] with ExtensionIdProvider {
  val DefaultOptions = Options(57600)

  def lookup(): ExtensionId[SerialExt] = Serial
  def createExtension(system: ExtendedActorSystem): SerialExt = new SerialExt(system)


  sealed trait Command
  sealed trait Event
  case class CommandFailed(cmd: Command, error: String) extends Event

  case class Open(port: String, options: Options, timeout: FiniteDuration) extends Command
  case class Opened(port: String, options: Options) extends Event

  case class Register(actor: ActorRef) extends Command

  case class Write(data: ByteString, ack: Event = NoAck) extends Command
  case class WriteText(data: String, ack: Event = NoAck) extends Event
  case class Received(data: ByteString) extends Event
  case object NoAck extends Event

  case object Close extends Command
  sealed trait PortClosed extends Event
  case object Closed extends PortClosed
  case class ErrorClosed(error: String) extends PortClosed
}

class SerialExt(system: ExtendedActorSystem) extends IO.Extension {
  val manager: ActorRef = system.systemActorOf(
    Props(classOf[SerialManager], this).withDeploy(Deploy.local), "IO-Serial"
  )
}
