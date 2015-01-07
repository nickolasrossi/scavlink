package scavlink.link

import scavlink.connection.frame.FrameError
import scavlink.message._
import scavlink.{BaseEventBus, EventMatcher, SubscribeToEvents}

trait LinkEvent
case class SentPacket(packet: Packet) extends LinkEvent
case class ReceiveError(error: FrameError) extends LinkEvent

/**
 * Distributes events that occur on a single connection.
 * Application actors may define and publish their own events by extending LinkEvent.
 * @author Nick Rossi
 */
class LinkEventBus extends BaseEventBus[LinkEvent]


/**
 * Convenient subscriptions.
 */
object SubscribeTo extends SubscribeToEvents[LinkEvent] {
  def allMessages: EventMatcher[LinkEvent] = complete {
    case event: Packet => true
  }

  def allMessages(systemId: SystemId) = complete {
    case Packet(From(_, `systemId`, _), _) => true
  }

  def allMessages(systemId: SystemId, componentId: ComponentId) = complete {
    case Packet(From(_, `systemId`, `componentId`), _) => true
  }

  def message(message: Class[_ <: Message]): EventMatcher[LinkEvent] = complete {
    case Packet(_, m) if message == m.getClass => true
  }

  def messageFrom(from: SystemId, message: Class[_ <: Message]): EventMatcher[LinkEvent] = complete {
    case Packet(From(_, `from`, _), m) if message == m.getClass => true
  }

  def messageFrom(fromSystem: SystemId, fromComponent: ComponentId, message: Class[_ <: Message]): EventMatcher[LinkEvent] = complete {
    case p@Packet(From(_, `fromSystem`, `fromComponent`), m) if message == m.getClass => true
  }

  def messageFromTo(from: SystemId, to: SystemId, message: Class[_ <: TargetSystem[_]]): EventMatcher[LinkEvent] = complete {
    case Packet(From(_, `from`, _), m: TargetSystem[_]) if message == m.getClass && m.targetSystem == to => true
  }

  def messages(messages: Class[_ <: Message]*): EventMatcher[LinkEvent] = complete {
    case Packet(_, m) if messages.contains(m.getClass) => true
  }

  def messagesFrom(from: SystemId, messages: Class[_ <: Message]*): EventMatcher[LinkEvent] = complete {
    case Packet(From(_, `from`, _), m) if messages.contains(m.getClass) => true
  }

  def messagesFrom(fromSystem: SystemId, fromComponent: ComponentId, messages: Class[_ <: Message]*): EventMatcher[LinkEvent] = complete {
    case p@Packet(From(_, `fromSystem`, `fromComponent`), m) if messages.contains(m.getClass) => true
  }

  def messagesFromTo(from: SystemId, to: SystemId, messages: Class[_ <: TargetSystem[_]]*): EventMatcher[LinkEvent] = complete {
    case Packet(From(_, `from`, _), m: TargetSystem[_]) if messages.contains(m.getClass) && m.targetSystem == to => true
  }

  def messagesExcept(messages: Class[_ <: Message]*): EventMatcher[LinkEvent] = complete {
    case Packet(_, m) if !messages.contains(m.getClass) => true
  }

  def messagesExcept(systemId: SystemId, messages: Class[_ <: Message]*): EventMatcher[LinkEvent] = complete {
    case Packet(From(_, `systemId`, _), m) if !messages.contains(m.getClass) => true
  }

  def messagesExcept(systemId: SystemId, componentId: ComponentId, messages: Class[_ <: Message]*): EventMatcher[LinkEvent] = complete {
    case p@Packet(From(_, `systemId`, `componentId`), m) if !messages.contains(m.getClass) => true
  }
}
