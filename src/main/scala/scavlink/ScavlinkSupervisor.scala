package scavlink

import akka.actor._
import com.codahale.metrics.{JmxReporter, MetricRegistry}
import scavlink.connection._
import scavlink.connection.marshal._
import scavlink.link.Vehicle
import scavlink.message.VehicleId
import scavlink.settings.ScavlinkConfig

case class StartConnection(settings: ConnectionSettings)
case class StopConnection(settings: ConnectionSettings)
case object GetVehicles
case object Shutdown

case class ScavlinkContext(events: ConnectionEventBus,
                           config: ScavlinkConfig,
                           metrics: MetricRegistry,
                           vehicleInitializers: Seq[VehicleInitializer],
                           linkAuthorizer: Option[KeyAuthorizer],
                           vehicleAuthorizer: Option[KeyAuthorizer],
                           marshallerFactory: MarshallerFactory)

object ScavlinkSupervisor {
  def props(sctx: ScavlinkContext, initializers: Seq[ScavlinkInitializer], connectionFactory: ConnectionFactory) =
    Props(classOf[ScavlinkSupervisor], sctx, initializers, connectionFactory)
}

/**
 * Top-level actor for an instance of the library.
 *
 * When the actor initializes, it starts all connections defined in the configuration.
 *
 * After initialization, application code can start more connections by sending the StartConnection message,
 * or stop any connection with the StopConnection message.
 * @author Nick Rossi
 */
class ScavlinkSupervisor(sctx: ScavlinkContext, initializers: Seq[ScavlinkInitializer], connectionFactory: ConnectionFactory)
  extends Actor with ActorLogging {

  private val reporter = JmxReporter.forRegistry(sctx.metrics).inDomain(self.path.name).build()
  reporter.start()

  private var connections: Map[ActorRef, ConnectionSettings] = Map.empty
  private var vehicles: Map[VehicleId, Vehicle] = Map.empty

  override def preStart() = {
    sctx.events.subscribe(self, ConnectionSubscribeTo.events(classOf[VehicleUp], classOf[VehicleDown]))

    initializers foreach { init =>
      init(self, sctx, context)
    }

    sctx.config.connections foreach { config =>
      if (connectionFactory.settings.isDefinedAt(config)) startConnection(connectionFactory.settings(config))
    }

    log.debug("Scavlink started")
  }

  override def postStop() = {
    sctx.events.unsubscribe(self)
    reporter.stop()
    log.debug("Scavlink stopped")
  }


  def receive: Receive = {
    case StartConnection(settings) => startConnection(settings)
    case StopConnection(settings) => stopConnection(settings)
    case GetVehicles => sender() ! Vehicles(vehicles)
    case Shutdown => context.stop(self)
    case Terminated(actor) => connections -= actor

    case VehicleUp(vehicle) =>
      vehicles += vehicle.id -> vehicle
      sctx.events.publish(Vehicles(vehicles))

    case VehicleDown(vehicle) =>
      vehicles -= vehicle.id
      sctx.events.publish(Vehicles(vehicles))
  }

  def startConnection(settings: ConnectionSettings): Unit = {
    if (findConnection(settings) == None) {
      if (connectionFactory.props.isDefinedAt((settings, sctx))) {
        val props = connectionFactory.props(settings, sctx)
        val actor = context.actorOf(props.withDispatcher(self.path.name + ".receive-dispatcher"), settings.actorName)
        log.debug(s"Started connection: $settings")
        connections += actor -> settings
        context.watch(actor)
      }
    }
  }

  def stopConnection(settings: ConnectionSettings): Unit = {
    findConnection(settings) foreach { actor =>
      context.stop(actor)
      log.debug(s"Stopped connection: $settings")
    }
  }

  private def findConnection(settings: ConnectionSettings): Option[ActorRef] =
    connections.find { case (a, c) => c.actorName == settings.actorName }.map(_._1)
}
