package scavlink.link

import akka.actor.ActorRef
import scavlink.message.Message
import scavlink.settings.ScavlinkConfig

/**
 * Represents a specific connection on which many vehicles may be active.
 *
 * @param address name of the link
 * @param events event bus on which this link's events are published
 * @param config config settings
 * @param packetSender actor for sending packets/messages
 * @param authKey successfully authorized token from AuthKey message, if authorization was performed
 * @author Nick Rossi
 */
case class Link(address: String,
                events: LinkEventBus,
                config: ScavlinkConfig,
                packetSender: ActorRef,
                authKey: Option[String]) {

  def send(message: Message) = packetSender ! message

  override def toString = address
}
