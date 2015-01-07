package scavlink.link.fence

import akka.actor.Status.Failure
import akka.actor.{Actor, ActorLogging, Props}
import scavlink.ScavlinkInitializer
import scavlink.connection._
import scavlink.link.operation.{Emergency, Op, OpException, OpResult}
import scavlink.link.telemetry.Telemetry
import scavlink.link.{LinkEvent, SubscribeTo, Vehicle}
import scavlink.state.State

trait TrafficControl {
  def subscribeTo: Set[Class[_ <: State]]
  def vehicleUp(vehicle: Vehicle): TrafficControl
  def vehicleDown(vehicle: Vehicle): TrafficControl
  def update(t: Telemetry): TrafficControl
  def tripped: Map[Vehicle, Op]
  def events: Map[Vehicle, LinkEvent]
}

object TrafficControlActor {
  def initializer(initial: TrafficControl): ScavlinkInitializer =
    (sup, sctx, actx) => List(actx.actorOf(props(sctx.events, initial), "traffic-control"))

  def props(events: ConnectionEventBus, initial: TrafficControl) =
    Props(classOf[TrafficControlActor], events, initial)
}

class TrafficControlActor(events: ConnectionEventBus, initial: TrafficControl)
  extends Actor with ActorLogging {

  private var control: TrafficControl = initial
  private var emergencyOps: Map[Op, Vehicle] = Map.empty

  override def preStart() = events.subscribeToAll(self)
  override def postStop() = events.unsubscribe(self)

  def receive: Receive = {
    case LinkUp(link) =>
      link.events.subscribe(self, SubscribeTo.complete {
        case Telemetry(_, state, _) if control.subscribeTo.contains(state.getClass) => true
      })

    case LinkDown(link) =>
      link.events.unsubscribe(self)

    case VehicleUp(vehicle) =>
      control = control.vehicleUp(vehicle)

    case VehicleDown(vehicle) =>
      control = control.vehicleDown(vehicle)

    case t: Telemetry =>
      control = control.update(t)

      control.tripped foreach {
        case (vehicle, op) if !emergencyOps.exists { case (o, v) => v == vehicle && o == op } =>
          log.debug(s"Tripped vehicle $vehicle")
          vehicle.navigation ! Emergency(op)
          emergencyOps += op -> vehicle

        case _ => // emergency op in progress
      }

      control.events foreach {
        case (vehicle, event) => vehicle.link.events.publish(event)
      }

    case result: OpResult =>
      emergencyOps -= result.op

    case Failure(result: OpException) =>
      emergencyOps -= result.op
  }
}
