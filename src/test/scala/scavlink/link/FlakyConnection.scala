package scavlink.link

import scavlink.message.Packet
import akka.actor.{Actor, Cancellable}

import scala.concurrent.duration._

/**
 * Simulates a connection where every Nth packet is not sent normally.
 */
trait FlakyConnection {
  _: MockVehicle =>

  def every: Int
  private var counter = 0

  protected def abnormalSend(packet: Packet)

  protected def send(packet: Packet) = {
    counter += 1
    if (counter == every) {
      counter = 0
      abnormalSend(packet)
    } else {
      sendFn(packet)
    }
  }
}


/**
 * Simulates a connection where every Nth packet is delayed.
 */
trait PacketDelays extends FlakyConnection {
  _: Actor with MockVehicle =>

  def delay: FiniteDuration

  // need to cancel any pending delayed packets when the actor is finished;
  // otherwise, the events still fire after the next test begins
  private var delays = List[Cancellable]()

  override def postStop(): Unit = delays.foreach(_.cancel())

  protected def abnormalSend(packet: Packet) =
    delays ::= context.system.scheduler.scheduleOnce(delay)(sendFn(packet))(context.dispatcher)
}

/**
 * Simulates a connection where every Nth packet is duplicated.
 */
trait PacketDuplication extends PacketDelays {
  _: Actor with MockVehicle =>

  def delay = 1.second

  override protected def abnormalSend(packet: Packet): Unit = {
    sendFn(packet)
    super.abnormalSend(packet)
  }
}

/**
 * Simulates a connection where every Nth packet is dropped.
 */
trait PacketDrops extends FlakyConnection {
  _: MockVehicle =>

  protected def abnormalSend(packet: Packet) = {}
}

/**
 * Simulates a connection that dies after N packets.
 */
trait DyingConnection {
  _: MockVehicle =>

  def after: Int
  private var counter = 0

  protected def send(packet: Packet) = {
    if (counter < after) {
      counter += 1
      sendFn(packet)
    }
  }
}