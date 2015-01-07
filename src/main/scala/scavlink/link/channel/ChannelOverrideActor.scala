package scavlink.link.channel

import akka.actor.{Actor, Cancellable, Props}
import scavlink.link.Vehicle
import scavlink.message.ComponentId

import scala.concurrent.duration._

sealed trait ChannelOverrideAction

case object DeactivateOverride extends ChannelOverrideAction

case class ChannelOverride(channel: Int, value: Double) extends ChannelOverrideAction {
  requireChannelValue(channel, value)
}

case class ChannelOverrides(values: Map[Int, Double]) extends ChannelOverrideAction {
  values.foreach { case (k, v) => requireChannelValue(k, v) }
}


object ChannelOverrideActor {
  def props(vehicle: Vehicle) = Props(classOf[ChannelOverrideActor], vehicle)
}

/**
 * Sends a channel override message to the vehicle on a fixed interval.
 * @author Nick Rossi
 */
class ChannelOverrideActor(vehicle: Vehicle) extends Actor {
  import context.dispatcher

  private var task: Option[Cancellable] = None

  def receive = {
    case ChannelOverrides(rc) => start(rc)
    case ChannelOverride(channel, value) => start(Map(channel -> value))
    case DeactivateOverride => stop()
  }

  private def start(values: Map[Int, Double]) = {
    stop()

    val message = channelOverrideMessage(vehicle.info.systemId, vehicle.info.defaultComponentId, values)
    val t = context.system.scheduler.schedule(Duration.Zero, vehicle.settings.channelOverrideInterval) {
      vehicle.link.send(message)
    }

    task = Some(t)
  }

  private def stop() = task match {
    case Some(t) =>
      t.cancel()
      task = None

    case None => //
  }
}
