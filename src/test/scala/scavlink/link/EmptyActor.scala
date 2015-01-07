package scavlink.link

import akka.actor.Actor

class EmptyActor extends Actor {
  def receive: Receive = Actor.emptyBehavior
}
