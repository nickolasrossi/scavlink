package scavlink.link.channel

import akka.actor.{Actor, ActorRef}
import scavlink.link.Vehicle
import scavlink.state.Channel

object ChannelTellAPI {
  implicit class ChannelAPI(val vehicle: Vehicle) {
    def setThrottle(throttle: Double)(implicit sender: ActorRef = Actor.noSender): Unit =
      overrideChannel(Channel.Throttle, throttle)

    def overrideChannels(values: Map[Int, Double])(implicit sender: ActorRef = Actor.noSender): Unit =
      vehicle.channels ! ChannelOverrides(values)

    def overrideChannel(channel: Int, value: Double)(implicit sender: ActorRef = Actor.noSender): Unit =
      vehicle.channels ! ChannelOverride(channel, value)

    def deactivateOverride()(implicit sender: ActorRef = Actor.noSender): Unit =
      vehicle.channels ! DeactivateOverride
  }
}