package scavlink

import java.util.concurrent.TimeUnit

import akka.event.{ActorEventBus, PredicateClassifier, ScanningClassification}
import com.codahale.metrics.CachedGauge

/**
 * Base class for event buses.
 * @tparam T event type
 */
class BaseEventBus[T] extends ActorEventBus with ScanningClassification with PredicateClassifier {
  type Event = T

  lazy val gauge = new CachedGauge[Long](10, TimeUnit.SECONDS) {
    def loadValue(): Long = subscribers.size()
  }


  protected def publish(event: Event, subscriber: Subscriber) = subscriber ! event

  protected def compareClassifiers(a: Classifier, b: Classifier) = a.hashCode compareTo b.hashCode

  protected def matches(classifier: Classifier, event: Event) = classifier(event)

  def subscribeToAll(subscriber: Subscriber) = subscribe(subscriber, _ => true)
}


/**
 * Convenient subscription filters.
 * @tparam T event type
 */
trait SubscribeToEvents[T] {
  val all: EventMatcher[T] = _ => true

  def event(event: Class[_ <: T]): EventMatcher[T] = e => event == e.getClass

  def events(events: Class[_ <: T]*): EventMatcher[T] = e => events.contains(e.getClass)

  def eventsExcept(events: Class[_ <: T]*): EventMatcher[T] = e => !events.contains(e.getClass)

  /**
   * Turn a partial event match into a total function by adding the fallback case statement.
   */
  def complete(pf: PartialFunction[T, Boolean]): EventMatcher[T] = pf orElse { case _ => false }
}
