package scavlink.link.parameter

import akka.actor.ActorSystem
import akka.testkit.TestProbe
import scavlink.link.SubscribeTo
import scavlink.link.parameter.ParameterAskAPI._
import scavlink.message.Packet
import scavlink.message.common.ParamValue
import scavlink.message.enums.MavParamType

import scala.concurrent.Await
import scala.concurrent.duration._

class ParameterCacheSpec(_system: ActorSystem) extends ParameterOpSpec(_system) {
  val duration = 15.seconds

  def this() = this(ActorSystem("ParameterCacheSpec"))

  "the ParameterCache" should {
    "produce a ReceivedParameters message on an intial Get" in
      withBasicResponder(params20) {
        val localProbe = new TestProbe(system)
        link.events.subscribe(localProbe.ref, SubscribeTo.event(classOf[ReceivedParameters]))

        val future1 = vehicle.getAllParameters
        val result1 = Await.result(future1, duration)

        localProbe.expectMsg(ReceivedParameters(vehicle, params20.mapValues(_.number)))
      }

    "return cached results on a second GetAll request" in
      withBasicResponder(params20) {
        Given("an initialized cache")
        val future1 = vehicle.getAllParameters
        val result1 = Await.result(future1, duration)

        val localProbe = new TestProbe(system)
        link.events.subscribe(localProbe.ref, SubscribeTo.message(classOf[ParamValue]))

        Then("a Get call should not generate any message traffic")
        val future2 = vehicle.getAllParameters
        val result2 = Await.result(future2, duration)
        result2.params shouldBe params20.mapValues(_.number)

        localProbe.expectNoMsg()
      }

    "return cached results on GetNamed request" in
      withBasicResponder(params20) {
        Given("an initialized cache")
        val future1 = vehicle.getAllParameters
        val result1 = Await.result(future1, duration)

        val localProbe = new TestProbe(system)
        link.events.subscribe(localProbe.ref, SubscribeTo.message(classOf[ParamValue]))

        Then("a GetNamed call should not generate any message traffic")
        val future2 = vehicle.getNamedParameters(params20.keys.take(2).toSet)
        val result2 = Await.result(future2, duration)
        result2.params shouldBe params20.take(2).mapValues(_.number)

        localProbe.expectNoMsg()
      }

    "return updated cache from GetAll after Set" in
      withBasicResponder(params20) {
        Given("an initialized cache")
        val future1 = vehicle.getAllParameters
        val result1 = Await.result(future1, duration)

        Then("a Set call should update the cache")
        val twoParams = result1.params.take(2).mapValues(_ + 99)
        val future2 = vehicle.setParameters(twoParams)
        val result2 = Await.result(future2, duration)

        And("a subsequent Get call")
        val localProbe = new TestProbe(system)
        link.events.subscribe(localProbe.ref, SubscribeTo.message(classOf[ParamValue]))

        val future3 = vehicle.getAllParameters
        val result3 = Await.result(future3, duration)
        result3.params shouldBe params20.mapValues(_.number) ++ twoParams

        Then("should not generate any message traffic")
        localProbe.expectNoMsg()
      }

    "update cache from unexpected ParamValue messages" in
      withBasicResponder(params20) {
        Given("an initialized cache")
        val future1 = vehicle.getAllParameters
        val result1 = Await.result(future1, duration)

        val localProbe = new TestProbe(system)
        link.events.subscribe(localProbe.ref, SubscribeTo.event(classOf[ReceivedParameters]))

        And("unexpected ParamValue messages")
        link.events.publish(Packet(id, systemId, componentId, ParamValue("INT_13", 999, MavParamType.INT32, 20, -1)))
        link.events.publish(Packet(id, systemId, componentId, ParamValue("INT_2", 1011, MavParamType.INT32, 20, -1)))

        Then("a ReceivedParameters event after at least 3 seconds")
        localProbe.expectMsg(7.seconds, ReceivedParameters(vehicle, Map("INT_13" -> 999.0f, "INT_2" -> 1011.0f)))
      }
  }
}