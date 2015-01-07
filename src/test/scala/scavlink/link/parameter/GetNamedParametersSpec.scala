package scavlink.link.parameter

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Success

class GetNamedParametersSpec(_system: ActorSystem) extends ParameterOpSpec(_system) {

  def this() = this(ActorSystem("GetNamedParametersSpec"))

  def getNamedParameters(expectedResultSet: Parameters, names: Set[String], actor: ActorRef, duration: Duration = 15.seconds): Parameters = {
    Given("sending a GetNamedParameters message to the actor")
    val future1 = (actor ? GetNamedParameters(names)).mapTo[GetNamedParametersResult]
    Await.result(future1, duration)

    Then("the GotNamedParameters result arrives")
    val Success(result1: GetNamedParametersResult) = future1.value.get
    result1.params shouldBe expectedResultSet
    result1.params
  }


  // tests

  "The GetNamedParameters operation actor" should {
    "return a future that eventually contains the parameter values" in
      withBasicResponder(oneParamOfEachType) {
        withGetNamedParameters { actor =>
          val names = Set("DOUBLE_5", "USHORT_9", "LONG_12")
          getNamedParameters(oneParamOfEachType.filterKeys(names.contains), names, actor)
        }
      }

    "handle a large number of names" in
      withBasicResponder(params32767) {
        withGetNamedParameters { actor =>
          val names = params32767.keySet
          getNamedParameters(params32767, names, actor, 30.seconds)
        }
      }

    "handle some names not found" in
      withBasicResponder(oneParamOfEachType) {
        withGetNamedParameters { actor =>
          val keys = oneParamOfEachType.keySet
          val names = keys.take(4) ++ keys.drop(4).map(s => s + "?")
          getNamedParameters(oneParamOfEachType.filterKeys(names.contains), names, actor)
        }
      }

    "handle a large number of names not found" in
      withBasicResponder(params32767) {
        withGetNamedParameters { actor =>
          val keys = params32767.keySet
          val names = keys.take(4) ++ keys.drop(4).map(s => s + "?")
          getNamedParameters(params32767.filterKeys(names.contains), names, actor, 30.seconds)
        }
      }

    "handle all names not found" in
      withBasicResponder(oneParamOfEachType) {
        withGetNamedParameters { actor =>
          val names = Set("jiodsf", "ue9te", "nvcdfs")
          getNamedParameters(Map.empty, names, actor)
        }
      }

    "handle duplicate packets" in
      withDuplicatingResponder(params32767, 10) {
        withGetNamedParameters { actor =>
          val names = params32767.keySet
          getNamedParameters(params32767, names, actor, 30.seconds)
        }
      }

    "handle dropped packets" in
      withDroppingResponder(params32767, 10) {
        withGetNamedParameters { actor =>
          val names = params32767.keySet
          getNamedParameters(params32767, names, actor, 30.seconds)
        }
      }

    "handle delayed packets" in
      withDelayingResponder(params32767, 10, 3.seconds) {
        withGetNamedParameters { actor =>
          val names = params32767.keySet
          getNamedParameters(params32767, names, actor, 30.seconds)
        }
      }
  }
}
