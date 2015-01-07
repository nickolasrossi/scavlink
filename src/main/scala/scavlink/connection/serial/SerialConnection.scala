package scavlink.connection.serial

import scavlink.connection.serial.Serial._
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.ByteString
import gnu.io.{SerialPort, SerialPortEvent, SerialPortEventListener}

object SerialConnection {
  // give this actor a dedicated thread from akka io's pinned-dispatcher for blocking writes
  def props(port: SerialPort): Props =
    Props(classOf[SerialConnection], port).withDispatcher("akka.io.pinned-dispatcher")
}

/**
 * Read/write actor for a serial connection.
 * Based on RXTX library.
 */
class SerialConnection(port: SerialPort) extends Actor with ActorLogging {
  // todo: queue writes when CTS is false
  private var clearToSend = true

  private val in = port.getInputStream
  private val out = port.getOutputStream

  override def postStop() = port.close()

  def receive: Receive = {
    case Register(handler) =>
      port.removeEventListener()
      port.addEventListener(readListener(handler))
      port.notifyOnDataAvailable(true)
      port.notifyOnCTS(true)

      context.become(registered(handler, write(handler)))
  }

  def registered(handler: ActorRef, write: (Array[Byte], Event) => Unit): Receive = {
    case Close =>
      context.stop(self)
      handler ! Closed
      log.debug(s"Closed serial port ${port.getName}")

    case Write(data, ack) =>
      write(data.toArray, ack)

    case WriteText(data, ack) =>
      write(data.getBytes, ack)

    case event@Register(newHandler) =>
      context.unbecome()
      self ! event
  }


  def write(handler: ActorRef)(bytes: Array[Byte], ack: Event) = {
    try {
      out.write(bytes)
      out.flush()
      if (ack != NoAck) handler ! ack
    } catch {
      case e: Exception =>
        context.stop(self)
        handler ! ErrorClosed(e.getMessage)
    }
  }

  def readListener(handler: ActorRef) = new SerialPortEventListener {
    private val readBuffer = Array.fill[Byte](1024)(0)

    // RXTX calls this listener from its own thread
    def serialEvent(event: SerialPortEvent): Unit = {
      event.getEventType match {
        case SerialPortEvent.DATA_AVAILABLE =>
          if (event.getNewValue) {
            try {
              val length = in.read(readBuffer, 0, readBuffer.length)
              if (length > 0) {
                handler ! Received(ByteString.fromArray(readBuffer, 0, length))
              }
            } catch {
              case e: Exception =>
                context.stop(self)
                handler ! ErrorClosed(e.getMessage)
            }
          }

        case SerialPortEvent.CTS =>
          clearToSend = event.getNewValue
      }
    }
  }
}
