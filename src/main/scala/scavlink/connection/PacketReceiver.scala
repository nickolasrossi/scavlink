package scavlink.connection

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.ByteString
import scavlink.ScavlinkContext
import scavlink.connection.frame.{FrameError, FrameReceiver}
import scavlink.link._
import scavlink.log.{LinkLoggingActor, LogSettings}
import scavlink.message.common.{AuthKey, Heartbeat}
import scavlink.message.enums.{MavAutopilot, MavType}
import scavlink.message.{Packet, SystemId, VehicleId}

import scala.util.Try

/**
 * Parses received data into packets and publishes them on the event bus for any type of link.
 * Vehicle lifecycle is handled here as well, when a packet from a new vehicle are detected.
 * @author Nick Rossi
 */
trait PacketReceiver extends Actor with ActorLogging {
  type PacketHandler = Either[FrameError, Packet] => Unit

  import context.dispatcher

  def address: String
  def sctx: ScavlinkContext
  val linkEvents = new LinkEventBus
  val heartbeat = sctx.config.heartbeat
  //  lazy val subscriberMetric = s"$address subscribers"

  private var rx: Option[FrameReceiver] = None
  private var link: Option[Link] = None
  private var logger: Option[ActorRef] = None

  // current packet handler - updated as link is established, then authorized
  private var packetHandler: PacketHandler = emptyHandler

  private var vehicles: Map[SystemId, (ActorRef, VehicleInfo)] = Map.empty
  private var vehicleKeys: Map[SystemId, String] = Map.empty
  private var vehicleHeartbeats: Map[SystemId, Long] = Map.empty

  override def preStart() = context.system.scheduler.schedule(heartbeat.timeout, heartbeat.timeout)(reapVehicles())

  override def postStop() = stop()

  /**
   * Start packet receiver when link is established.
   */
  def start(writeData: ByteString => Unit, fallback: Receive): Unit = {
    //    sctx.metrics.register(subscriberMetric, linkEvents.gauge)

    val senderProps = PacketSender.props(
      address, VehicleId.GroundControl, heartbeat, linkEvents, sctx.marshallerFactory, writeData, fallback, Some(sctx.metrics))

    rx = Some(new FrameReceiver(address, sctx.marshallerFactory, Some(sctx.metrics)))

    val logSettings = LogSettings(sctx.config.root)
    if (logSettings.isEnabled) {
      logger = Try(context.actorOf(LinkLoggingActor.props(address, logSettings.path), "logger")).toOption
    }

    // if link authorization is required, don't create the link until a valid AuthKey is received
    packetHandler = sctx.linkAuthorizer match {
      case Some(authorizer) => waitForAuthKey(senderProps)
      case None => linkUp(senderProps, None)
    }
  }

  /**
   * Signal that the link is ready to send and receive packets.
   */
  def linkUp(props: Props, authKey: Option[String]): PacketHandler = {
    // start packet sender now
    val packetSender = context.actorOf(props, "sender")

    val _link = new Link(address, linkEvents, sctx.config, packetSender, authKey)
    link = Some(_link)

    sctx.events.publish(LinkUp(_link))
    linkAuthorized
  }

  /**
   * Process received data.
   */
  def receiveData(data: ByteString): Unit = {
    rx.get.receivedData(data) foreach packetHandler
    logger.foreach(_ ! data)
  }

  /**
   * Signal that a new vehicle is detected and ready to receive packets.
   * Starts supervisor actor for the vehicle, which initializes all business logic actors.
   */
  def startVehicle(vehicleInfo: VehicleInfo) = link foreach { _link =>
    val systemId = vehicleInfo.systemId
    val props = VehicleSupervisor.props(sctx.events, _link, vehicleInfo, sctx.vehicleInitializers)
    val actor = context.actorOf(props, "vehicle_" + systemId + "_" + System.currentTimeMillis())

    vehicles += systemId ->(actor, vehicleInfo)
  }

  /**
   * Signal that a vehicle has disappeared from the link.
   */
  def stopVehicle(systemId: SystemId): Unit = {
    vehicles.get(systemId) foreach { case (actor, vehicleInfo) =>
      context.stop(actor)
      vehicles -= systemId
      vehicleKeys -= systemId
      vehicleHeartbeats -= systemId
      VehicleNumberPool.free(vehicleInfo.number.number)
    }
  }

  /**
   * Shut down the link and empty all state data.
   */
  def stop() = link foreach { _link =>
    //    sctx.metrics.remove(subscriberMetric)
    context.stop(_link.packetSender)

    logger.foreach(context.stop)
    logger = None

    vehicles.keySet.foreach(stopVehicle)
    vehicles = Map.empty
    vehicleKeys = Map.empty
    vehicleHeartbeats = Map.empty

    packetHandler = emptyHandler
    rx = None

    sctx.events.publish(LinkDown(_link))
    link = None
  }

  /**
   * Stop any vehicle whose last heartbeat is older than the heartbeat-timeout setting.
   */
  def reapVehicles(): Unit = {
    log.debug("Checking heartbeats")
    val now = System.currentTimeMillis()
    vehicleHeartbeats foreach { case (systemId, lastHeartbeat) =>
      if (now - lastHeartbeat > heartbeat.timeout.toMillis) {
        val id = vehicles(systemId)._2.id
        log.warning(s"Vehicle $id went silent")
        stopVehicle(systemId)
      }
    }
  }


  private def emptyHandler: PacketHandler = x => {}

  /**
   * Handles packets when the link is waiting for authorization.
   * No packets are published until a valid AuthKey is received.
   */
  private def waitForAuthKey(props: Props): PacketHandler = {
    case Right(Packet(from, AuthKey(key))) if from.systemId == heartbeat.thisSystemId =>
      sctx.linkAuthorizer foreach { authorizer =>
        if (authorizer(key)) {
          log.debug(s"Authorized key from $from")
          linkUp(props, Some(key))
        } else {
          log.debug(s"Invalid key from $from")
        }
      }

    case Left(error) =>
      linkEvents.publish(ReceiveError(error))

    case _ => //
  }

  /**
   * Handles packets when the link is authorized.
   */
  private def linkAuthorized: PacketHandler = {
    case Right(packet@Packet(from, AuthKey(key))) if sctx.vehicleAuthorizer.isDefined && !vehicleKeys.contains(from.systemId) =>
      sctx.vehicleAuthorizer foreach { authorizer =>
        if (authorizer(key)) {
          log.debug(s"Authorized key from $from")
          vehicleKeys += from.systemId -> key
          linkEvents.publish(packet)
        } else {
          log.debug(s"Invalid key from $from")
        }
      }

    // this is where a new Vehicle is initialized when its heartbeat is first detected
    case Right(packet@Packet(from, Heartbeat(vehicleType, autopilot, _, _, _, _))) if !vehicles.contains(from.systemId) =>
      val key = vehicleKeys.get(from.systemId)
      if (sctx.vehicleAuthorizer.isEmpty || key.isDefined) {
        val vehicleNumber = VehicleNumber(VehicleNumberPool.next())
        val vehicleInfo = VehicleInfo(from.id, vehicleNumber, from.systemId, from.componentId, MavType(vehicleType), MavAutopilot(autopilot), key)
        startVehicle(vehicleInfo)
        linkEvents.publish(packet)
      }

    case Right(packet@Packet(from, _: Heartbeat)) =>
      vehicleHeartbeats += from.systemId -> System.currentTimeMillis()
      linkEvents.publish(packet)

    case Right(packet) =>
      linkEvents.publish(packet)

    case Left(error) =>
      linkEvents.publish(ReceiveError(error))
  }
}
