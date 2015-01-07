package scavlink.connection.serial

import scavlink.connection.serial.Serial._
import akka.actor.{Actor, ActorLogging, Props}
import gnu.io.{CommPort, CommPortIdentifier, SerialPort}

import scala.concurrent.duration.Duration

/**
 * Establishes connection to a serial device.
 * Part of Akka IO integration.
 */
class SerialManager(ext: SerialExt) extends Actor with ActorLogging {
  def receive: Receive = {
    case cmd@Open(portName, options, timeout) =>
      var port: CommPort = null

      try {
        val portId = CommPortIdentifier.getPortIdentifier(portName)
        port = portId.open("IO-Serial", timeout.toMillis.toInt)
        log.debug(s"Opened serial port $portName")

        port match {
          case serialPort: SerialPort =>
            serialPort.setSerialPortParams(
              options.bitRate, options.dataBits.id, options.stopBits.id, options.parity.id)

            val serialActor = context.actorOf(SerialConnection.props(serialPort))
            sender().tell(Opened(portName, options), serialActor)
            log.debug(s"Started SerialConnection actor for $portName")

          case p =>
            throw new Exception(s"Unsupported port type (${p.getClass.getSimpleName})")
        }
      } catch {
        case e: Exception =>
          if (port != null) port.close()
          sender ! CommandFailed(cmd, e.getMessage)
          log.debug(s"Error opening $portName: ${e.getMessage}")
      }
  }
}
