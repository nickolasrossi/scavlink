import akka.actor.{ActorRef, ActorRefFactory}
import scavlink.connection.udp.UdpBridge
import scavlink.link.Vehicle
import scavlink.link.fence.FenceActor
import scavlink.task.TaskInitializer

package object scavlink {
  type EventMatcher[T] = T => Boolean
  type PartialEventMatcher[T] = PartialFunction[T, Boolean]

  type ScavlinkInitializer = (ActorRef, ScavlinkContext, ActorRefFactory) => Seq[ActorRef]
  type VehicleInitializer = (Vehicle, ActorRefFactory) => Seq[ActorRef]

  type KeyAuthorizer = String => Boolean


  val DefaultScavlinkInitializers = Seq(
    UdpBridge.initializer,
    TaskInitializer
//    TrafficControlActor.initializer(ProximityMonitor(20, 1))
  )

  val DefaultVehicleInitializers = Seq(
    FenceActor.initializer
  )
}
