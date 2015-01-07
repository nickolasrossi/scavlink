package scavlink

import akka.actor.{Actor, ActorRef, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import com.codahale.metrics.MetricRegistry
import com.typesafe.config.Config
import scavlink.connection._
import scavlink.connection.frame.DefaultMarshallerFactory
import scavlink.connection.marshal.MarshallerFactory
import scavlink.settings.ScavlinkConfig

import scala.concurrent.Future

/**
 * Handle to an instance of the Scavlink library.
 * Only one instance is typically needed in an application, as it will handle multiple connections
 * and multiple vehicles per connection.
 *
 * @param name name of the instance
 * @param events where library-wide events are published
 * @param supervisor top-level supervisor for all actors
 * @author Nick Rossi
 */
class ScavlinkInstance(val name: String, val config: ScavlinkConfig, val events: ConnectionEventBus, val supervisor: ActorRef) {
  def startConnection(settings: ConnectionSettings)(implicit sender: ActorRef = Actor.noSender): Unit =
    supervisor ! StartConnection(settings)

  def stopConnection(settings: ConnectionSettings)(implicit sender: ActorRef = Actor.noSender): Unit =
    supervisor ! StopConnection(settings)

  def getVehicles()(implicit sender: ActorRef = Actor.noSender): Unit =
    supervisor ! GetVehicles

  def askVehicles()(implicit timeout: Timeout): Future[Vehicles] =
    (supervisor ? GetVehicles).mapTo[Vehicles]

  def shutdown()(implicit sender: ActorRef = Actor.noSender) =
    supervisor ! Shutdown
}

object ScavlinkInstance {
  /**
   * Initializes an instance of the Scavlink library.
   *
   * The configuration object is taken from the actor system if not provided as an override here.
   * Scavlink configuration should be under the root level in a block called "scavlink"
   * (or the alternate instance name passed in here).
   *
   * The default connection factory provides a TCP client, UDP listener, and serial port connection.
   * An application may extend it with additional handlers.
   *
   * The default marshaller factory returns a message marshaller for a given autopilot.
   * An application may extend it with marshallers for other autopilot types.
   *
   * @param system Akka actor system
   * @param config optional config override
   * @param linkAuthorizer if specified, requires an initial valid AuthKey from remote GCS when a link is established
   * @param vehicleAuthorizer if specified, requires an initial valid AuthKey from each vehicle
   * @param initializers callbacks for application-specific initialization when the library starts
   * @param vehicleInitializers callbacks for application-specific initialization when a new vehicle appears
   * @param connectionFactory factory that knows how to create new communication links
   * @param marshallerFactory factory that knows how to obtain a marshaller for a given autopilot type
   * @param name optional name to differentiate multiple instances (defaults to "scavlink")
   * @author Nick Rossi
   */
  def apply(system: ActorSystem,
            name: String = "scavlink",
            config: Option[Config] = None,
            initializers: Seq[ScavlinkInitializer] = DefaultScavlinkInitializers,
            vehicleInitializers: Seq[VehicleInitializer] = DefaultVehicleInitializers,
            linkAuthorizer: Option[KeyAuthorizer] = None,
            vehicleAuthorizer: Option[KeyAuthorizer] = None,
            connectionFactory: ConnectionFactory = DefaultConnectionFactory,
            marshallerFactory: MarshallerFactory = DefaultMarshallerFactory.apply): ScavlinkInstance = {

    val metrics = new MetricRegistry
    val events = new ConnectionEventBus
    metrics.register("ConnectionEventBus subscribers", events.gauge)

    val scfg = new ScavlinkConfig(config.getOrElse(system.settings.config), name)
    val sctx = ScavlinkContext(events, scfg, metrics, vehicleInitializers, linkAuthorizer, vehicleAuthorizer, marshallerFactory)
    val supervisor = system.actorOf(ScavlinkSupervisor.props(sctx, initializers, connectionFactory), name)

    new ScavlinkInstance(name, scfg, events, supervisor)
  }
}
