package scavlink.link.parameter

import scavlink.link.operation.OpTimeout
import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Success

class GetAllParametersSpec(_system: ActorSystem) extends ParameterOpSpec(_system) {

  def this() = this(ActorSystem("GetAllParametersSpec"))

  /**
   * Invoke GetAllParameters against the running actor expecting the given result set.
   * Duration defaults to 5 seconds. Most tests should finish in well under 5 seconds.
   */
  def getAllParameters(expectedResultSet: Parameters, actor: ActorRef, duration: Duration = 30.seconds): Parameters = {
    Given("sending a GetAllParameters message to the actor")
    val op = GetAllParameters()
    val future1 = (actor ? op).mapTo[GetAllParametersResult]
    Await.result(future1, duration)

    Then("the GotAllParameters result arrives")
    val Success(result1: GetAllParametersResult) = future1.value.get

    result1.params.size shouldBe expectedResultSet.size
    result1.params shouldBe expectedResultSet
    result1.op shouldBe op
    result1.params
  }


  // tests

  "The GetAllParameters actor" should {
    "return a future that eventually contains the whole parameter list" in
      withBasicResponder(oneParamOfEachType) {
        withGetAllParameters { actor =>
          getAllParameters(oneParamOfEachType, actor)
        }
      }

    "return every data type properly" in
      withBasicResponder(oneParamOfEachType) {
        withGetAllParameters { actor =>
          val params = getAllParameters(oneParamOfEachType, actor)

          And("and each MavParamValue type should convert to the proper language type")
          params("FLOAT_4") shouldBe 4f
          params("DOUBLE_5") shouldBe 5D
          params("BYTE_6") shouldBe 6
          params("UBYTE_7") shouldBe 7
          params("SHORT_8") shouldBe 8
          params("USHORT_9") shouldBe 9
          params("INT_10") shouldBe 10
          params("UINT_11") shouldBe 11
          params("LONG_12") shouldBe 12L
          params("ULONG_13") shouldBe 13L
        }
      }

    "return unsigned values with high bits properly" in
      withBasicResponder(highBitParams) {
        withGetAllParameters { actor =>
          val params = getAllParameters(highBitParams, actor)
          params("UBYTE") shouldBe 222
          params("USHORT") shouldBe 60000
        }
      }

    "handle real parameters taken from the SITL simulator" in
      withBasicResponder(actualSitlParams) {
        withGetAllParameters { actor =>
          getAllParameters(actualSitlParams, actor)
        }
      }

    "handle 32767 parameters (just before param_index: Short goes negative)" in
      withBasicResponder(params32767) {
        withGetAllParameters { actor =>
          getAllParameters(params32767, actor)
        }
      }

    "handle > 32767 parameters (param_index: Short will go negative)" in
      withBasicResponder(params40000) {
        withGetAllParameters { actor =>
          getAllParameters(params40000, actor)
        }
      }

    "handle 65534 parameters (max value for paramCount excluding -1 value)" in
      withBasicResponder(maxParams) {
        withGetAllParameters { actor =>
          getAllParameters(maxParams, actor)
        }
      }

    "handle duplicate messages for 20 parameters" in
      withDuplicatingResponder(params20, 2) {
        withGetAllParameters { actor =>
          getAllParameters(params20, actor)
        }
      }

    "handle duplicate messages for 40000 parameters" in
      withDuplicatingResponder(params40000, 2) {
        withGetAllParameters { actor =>
          getAllParameters(params40000, actor)
        }
      }

    "handle dropped messages for 20 parameters" in
      withDroppingResponder(params20, 3) {
        withGetAllParameters { actor =>
          getAllParameters(params20, actor)
        }
      }

    "handle dropped messages for 40000 parameters" in
      withDroppingResponder(params40000, 10) {
        withGetAllParameters { actor =>
          getAllParameters(params40000, actor, 60.seconds)
        }
      }

    "handle delayed messages for 20 parameters" in
      withDelayingResponder(params20, 3, 3.seconds) {
        withGetAllParameters { actor =>
          getAllParameters(params20, actor, 60.seconds)
        }
      }

    "handle delayed messages for 40000 parameters" in
      withDelayingResponder(params40000, 10, 3.seconds) {
        withGetAllParameters { actor =>
          getAllParameters(params40000, actor, 60.seconds)
        }
      }

    "time out properly when the connection stops responding " in
      withDyingResponder(params20, 10) {
        withGetAllParameters { actor =>
          Given("sending a GetAllParameters message to a dying connection")
          val op = GetAllParameters()
          val future1 = (actor ? op).mapTo[GetAllParametersResult]

          Then("the future should return an OpTimeout failure")
          an [OpTimeout] should be thrownBy Await.result(future1, 30.seconds)
        }
      }
  }
}
