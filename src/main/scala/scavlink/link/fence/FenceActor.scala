package scavlink.link.fence

import akka.actor.Status.Failure
import akka.actor.{ActorLogging, Actor, Props}
import scavlink.VehicleInitializer
import scavlink.coord._
import scavlink.link.nav.{SetVehicleMode, NavOps}
import scavlink.link.nav.NavTellAPI._
import scavlink.link.operation._
import scavlink.link.telemetry.Telemetry
import scavlink.link.{LinkEvent, SubscribeTo, Vehicle}
import scavlink.message.Mode
import scavlink.state.LocationState

sealed trait FenceEvent extends LinkEvent {
  def vehicle: Vehicle
  def fences: Set[FenceBinding]
  def state: LocationState

  override def toString = {
    val name = this.getClass.getSimpleName
    s"$name(${ vehicle.id } fences=${ fences.map(_.name).mkString(",") })"
  }
}

case class FenceInside(vehicle: Vehicle, fences: Set[FenceBinding], state: LocationState) extends FenceEvent
case class FenceEnter(vehicle: Vehicle, fences: Set[FenceBinding], state: LocationState, oldState: LocationState) extends FenceEvent
case class FenceExit(vehicle: Vehicle, fences: Set[FenceBinding], state: LocationState, oldState: LocationState) extends FenceEvent
case class FenceBreach(vehicle: Vehicle, fences: Set[FenceBinding], state: LocationState, lastGood: LocationState) extends FenceEvent


object FenceActor {
  def initializer: VehicleInitializer =
    (vehicle, actx) => List(actx.actorOf(props(vehicle, FenceSettings(vehicle.link.config.root)), "fence"))

  def props(vehicle: Vehicle, settings: FenceSettings) = Props(classOf[FenceActor], vehicle, settings)
  def subscribeToBreach(vehicle: Vehicle) = SubscribeTo.complete {
    case e: FenceBreach if e.vehicle == vehicle => true
  }
}

/**
 * Reports on and enforces geofences for a single vehicle.
 * Fence events are published as frequently as LocationState telemetry events.
 * @author Nick Rossi
 */
class FenceActor(vehicle: Vehicle, settings: FenceSettings) extends Actor with ActorLogging {
  val events = vehicle.link.events
  val fences = FenceBinding.filter(settings.bindings, vehicle.info)

  private var activeBreach: Boolean = false
  private var lastStatus: FenceInside = FenceInside(vehicle, Set.empty, LocationState(vehicle.id))
  private var lastGoodState: Option[LocationState] = None

  override def preStart() = {
    log.debug("Enforcing fences:")
    fences.foreach(f => log.debug(f.toString))
    events.subscribe(self, Telemetry.subscribeTo(vehicle.id, Set(classOf[LocationState])))
  }

  override def postStop() = events.unsubscribe(self)


  def receive: Receive = {
    case Telemetry(_, state: LocationState, oldState: LocationState) if state.location.latlon != LatLonSpace.zero =>
      val location = state.location
      val (inside, outside) = fences.partition(_.fence.contains(location))

      val status = FenceInside(vehicle, inside, state)
      events.publish(status)

      val entered = inside -- lastStatus.fences
      if (entered.nonEmpty && lastStatus.state.location.latlon != LatLonSpace.zero) {
        events.publish(FenceEnter(vehicle, entered, state, oldState))
      }

      val exited = lastStatus.fences -- inside
      if (exited.nonEmpty) events.publish(FenceExit(vehicle, exited, state, oldState))

      lastStatus = status

      val breaches = inside.filter(_.mode == FenceMode.StayOut) ++ outside.filter(_.mode == FenceMode.StayIn)
      if (breaches.nonEmpty) {
        events.publish(FenceBreach(vehicle, breaches, state, lastGoodState.getOrElse(LocationState(vehicle.id))))
        if (!activeBreach) {
          activeBreach = true
          doFenceAction(settings.breachAction)
        }
      } else {
        activeBreach = false
        lastGoodState = Some(state)
      }

    case Failure(_: ConversationFailed) =>
      // todo: handle Loiter failure
      if (activeBreach && settings.breachAction != FenceBreachAction.Loiter) {
        doFenceAction(FenceBreachAction.Loiter)
      }
  }

  def doFenceAction(action: FenceBreachAction.Value): Unit = {
    if (action != FenceBreachAction.None) {
      val op: Op = action match {
        case FenceBreachAction.Loiter => SetVehicleMode(vehicle, Mode.Loiter)
        case FenceBreachAction.Land => SetVehicleMode(vehicle, Mode.Land)
        case FenceBreachAction.ReturnToLaunch => SetVehicleMode(vehicle, Mode.ReturnToLaunch)
        case FenceBreachAction.LastSafeLocation => lastGoodState match {
          case Some(ls) => stopAndGotoLocation(vehicle, ls.location)
          case None => SetVehicleMode(vehicle, Mode.Loiter)
        }
      }

      vehicle.navigation ! Emergency(op)
    }
  }

  /**
   * Quick conversation that interrupts any mission in progress,
   * then navigates the vehicle to a specific location.
   * Used during a fence breach to move the vehicle to a safe place.
   */
  def stopAndGotoLocation(vehicle: Vehicle, point: Geo): Conversation = Conversation(List(
    NavOps.setMode(vehicle, Mode.Loiter),
    NavOps.setMode(vehicle, Mode.Guided),
    NavOps.guidedPoint(vehicle, point)
  ))
}
