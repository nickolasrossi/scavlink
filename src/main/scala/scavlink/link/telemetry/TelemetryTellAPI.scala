package scavlink.link.telemetry

import akka.actor.{Actor, ActorRef}
import scavlink.link.Vehicle

import scala.concurrent.duration._

/**
 * Note that no methods are provided to get current telemetry data, as they would be
 * too easily misused to poll the data at high frequency, which is bad in a high
 * concurrency environment. The right way to receive telemetry is to subscribe
 * to telemetry events on a vehicle's LinkEventBus.
 * @author Nick Rossi
 */
object TelemetryTellAPI {
  implicit class TelemetryAPI(val vehicle: Vehicle) {
    /**
     * Activate the specified telemetry streams and turn off all others.
     *
     * The "publish" parameter controls when telemetry events will be published.
     *
     * If "OnInterval", events are published on the same interval as the firmware from whatever
     * packets have been accumulated since the last publish.
     *
     * If "Immediately", a telemetry event is published upon receipt of any relevant packet.
     * This can slightly reduce delay of receiving a telemetry event as compared with OnInterval,
     * but produces more actor message traffic, so use it with care.
     *
     * @param streams set of telemetry streams to become active
     * @param interval how often firmware shuold produce telemetry messages (default: 1 second)
     * @param publish when to publish events: on a timed interval, or on every received message (default: Interval)
     */
    def setTelemetryStreams(streams: StateGenerators,
                            interval: FiniteDuration,
                            publish: PublishMode = PublishOnInterval)
                           (implicit sender: ActorRef = Actor.noSender): Unit =
      vehicle.telemetry ! SetTelemetryStreams(streams, interval, publish)

    def stopAllTelemetry()(implicit sender: ActorRef = Actor.noSender): Unit =
      vehicle.telemetry ! StopAllTelemetry
  }
}