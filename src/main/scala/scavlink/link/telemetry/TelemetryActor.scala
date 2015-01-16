package scavlink.link.telemetry

import akka.actor._
import com.typesafe.config.Config
import scavlink.link._
import scavlink.message._
import scavlink.message.common.RequestDataStream
import scavlink.message.enums.MavDataStream
import scavlink.settings.SettingsCompanion
import scavlink.state.{State, StateGenerator}

import scala.collection.mutable
import scala.concurrent.duration._

sealed trait TelemetryAction

case object StopAllTelemetry extends TelemetryAction

case class SetTelemetryStreams(streams: StateGenerators,
                               interval: FiniteDuration,
                               publish: PublishMode) extends TelemetryAction {
  require(interval >= 20.milliseconds)
}


sealed trait PublishMode
case object PublishImmediate extends PublishMode
case object PublishOnInterval extends PublishMode


object TelemetryActor {
  def props(vehicle: Vehicle) = Props(classOf[TelemetryActor], vehicle)
}

/**
 * Receives telemetry messages, aggregates them into state objects, and publishes the objects as events.
 * @author Nick Rossi
 */
class TelemetryActor(vehicle: Vehicle) extends Actor with ActorLogging {

  import context.dispatcher

  private type StateMap = Map[StateGenerator[_ <: State], State]
  private case class RetrySetStreams(interval: FiniteDuration, tries: Int)

  val link = vehicle.link
  val targetSystem = vehicle.info.systemId
  val targetComponent = vehicle.info.defaultComponentId
  val settings = TelemetrySettings(link.config.root)

  val Start = 1.toByte
  val Stop = 0.toByte

  // publishes changes on a timer
  private var publisher: Option[Cancellable] = None

  // quick lookup of which generators are relevant to a particular message
  private var generatorsByMessage: Map[Int, StateGenerators] = Map.empty

  // latest relevant packets received (use mutable map for efficient processing)
  private val packets: mutable.Map[Int, Packet] = mutable.Map.empty

  // most recent states
  private var states: StateMap = Map.empty

  // which requested states have yet to be received
  private var expecting: StateGenerators = Set.empty


  override def preStart() = {
    if (vehicle.settings.autostartTelemetry) {
      self ! SetTelemetryStreams(
        DefaultTelemetryStreams.all, 
        settings.autostartInterval,
        if (settings.autostartImmediateMode) PublishImmediate else PublishOnInterval
      )
    }
  }

  override def postStop() = {
    publisher.foreach(_.cancel())
    link.events.unsubscribe(self)
  }

  def receive: Receive = {
    case SetTelemetryStreams(newStreams, interval, publishOn) =>
      log.debug(s"Activating telemetry: ${newStreams.map(_.stateType.getSimpleName)} every $interval")
      setStreams(newStreams, interval, publishOn)

    case StopAllTelemetry =>
      setStreams(Set.empty, 1.second, PublishImmediate)

    case RetrySetStreams(interval, tries) =>
      if (expecting.nonEmpty) {
        log.debug(s"Requesting again: $expecting")
        requestStreams(expecting, interval, tries)
      }

    case packet@Packet(_, msg) =>
      packets.get(msg._id) match {
        case Some(existing) if packet.seq <= existing.seq =>
        // do nothing if latest packet has duplicate or earlier sequence number.
        // only the latest telemetry update is ever relevant.

        case _ =>
          packets(msg._id) = packet
          // if no publisher task, publish changes immediately
          if (!publisher.isDefined) {
            publishTelemetry()
          }
      }
  }

  /**
   * Publish states based on messages received since last publish.
   * Lots of side-effecting here, but it's more efficient to compile all relevant changes in one pass.
   */
  private def publishTelemetry(): Unit = {
    val newStates: mutable.Map[StateGenerator[_ <: State], State] = mutable.Map.empty

    // update all relevant states based on all received messages since last publish
    for {
      (id, packet) <- packets
      gen <- generatorsByMessage.getOrElse(id, Set.empty)
    } yield {
      val state = (newStates orElse states)(gen)
      val newState = gen.extract.applyOrElse[(State, Message), State]((state, packet.message), _._1)
      newStates(gen) = newState
      expecting -= gen
    }

    newStates collect {
      case (gen, state) => link.events.publish(Telemetry(vehicle, state, states(gen)))
    }

    // reset for next publish
    packets.clear()
    states ++= newStates
  }

  /**
   * Activate the requested streams and deactivate any old ones not in the new list.
   */
  private def setStreams(newGenerators: StateGenerators, interval: FiniteDuration, publish: PublishMode): Unit = {
    // first, shut off all existing streams
    publisher.map(_.cancel())
    publisher = None

    link.events.unsubscribe(self)
    link.send(RequestDataStream(targetSystem, targetComponent, MavDataStream.ALL.id.toByte, 0, Stop))

    // subscribe to the new message set
    val newMessages = newGenerators.flatMap(_.messages)
    if (newMessages.nonEmpty) {
      link.events.subscribe(self, SubscribeTo.messagesFrom(targetSystem, newMessages.map(_.getClass).toSet.toSeq: _*))
    }

    // build a list of generators by message id for quick lookup on publish
    generatorsByMessage = buildGeneratorsByMessage(newGenerators)

    // retain messages and states relevant to the new streams
    val newMessageIds = newMessages.map(_._id)
    packets.retain { case (id, _) => newMessageIds.contains(id)}

    // populate new state objects where needed
    states = newGenerators.map { gen =>
      gen -> states.getOrElse(gen, gen.create(vehicle.id))
    }.toMap

    // start a new publisher instance if the interval is defined
    publisher = publish match {
      case PublishOnInterval if newMessages.nonEmpty =>
        val task = context.system.scheduler.schedule(interval, interval)(publishTelemetry())
        Some(task)

      case _ => None
    }

    expecting = newGenerators
    requestStreams(newGenerators, interval, 0)
  }

  /**
   * Send relevant data stream request messages to the vehicle.
   */
  private def requestStreams(generators: StateGenerators, interval: FiniteDuration, tries: Int): Unit = {
    // use requested interval to determine message frequency (min 1 message per second)
    val frequency = 1000 / math.min(interval.toMillis, 1000)
    val streamEnums = generators.flatMap(_.streams).toSet
    streamEnums foreach { e =>
      link.send(RequestDataStream(targetSystem, targetComponent, e.id.toByte, frequency.toShort, Start))
    }

    if (tries < settings.retries) {
      context.system.scheduler.scheduleOnce(settings.timeout, self, RetrySetStreams(interval, tries + 1))
    } else {
      log.debug(s"Giving up on $expecting")
      expecting = Set.empty
    }
  }

  /**
   * Build map of message id to relevant generators.
   */
  private def buildGeneratorsByMessage(generators: StateGenerators): Map[Int, StateGenerators] = {
    val genmap: mutable.Map[Int, StateGenerators] = mutable.Map.empty

    for {
      gen <- generators
      msg <- gen.messages
    } yield {
      val set = genmap.getOrElse(msg._id, Set.empty)
      genmap(msg._id) = set + gen
    }

    genmap.toMap
  }
}


case class TelemetrySettings(autostartInterval: FiniteDuration,
                             autostartImmediateMode: Boolean,
                             timeout: FiniteDuration,
                             retries: Int)

object TelemetrySettings extends SettingsCompanion[TelemetrySettings]("telemetry") {
  def fromSubConfig(config: Config): TelemetrySettings =
    TelemetrySettings(
      getDuration(config, "autostart-interval"),
      config.getBoolean("autostart-immediate-mode"),
      getDuration(config, "request-timeout"),
      config.getInt("request-retries")
    )
}
