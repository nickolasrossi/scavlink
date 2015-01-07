package scavlink.link.parameter

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Success, Try}

class SetParametersSpec(_system: ActorSystem) extends ParameterOpSpec(_system) {

  def this() = this(ActorSystem("SetParametersSpec"))

  def generateSubset(params: Parameters): Parameters =
    params.drop(params.size / 10).map { case (name, v) =>
      name -> random.nextInt(1 << 16).toFloat
    }

  def setParameters(params: Map[String, AnyVal], actor: ActorRef): Parameters = {
    Given("sending a SetParameters message to the actor")
    val future1 = (actor ? SetParameters(params)).mapTo[SetParametersResult]
    Await.result(future1, 120.seconds)

    Then("the DidSetParameters result contains all the changed parameters")
    val Success(result1: SetParametersResult) = future1.value.get

    result1.params.size shouldBe params.size
    result1.params foreach { case (k, v) =>
      v shouldBe params(k)
    }

    result1.params
  }

  def setSomeParameters(params: Map[String, AnyVal], expected: Parameters, actor: ActorRef): Parameters = {
    Given("sending a SetParameters message with some existing and some not")
    val future1 = (actor ? SetParameters(params)).mapTo[SetParametersResult]

    try {
      Await.result(future1, 120.seconds)
      fail("expected DidSetSomeParameters exception")
    } catch {
      case result1: SetParametersPartialFailure =>
        Then("the DidSetSomeParameters result identifies which parameters changed and which didn't")
        result1.params shouldBe expected
        result1.notSet shouldBe params -- expected.keySet
        result1.params
    }
  }


  // tests

  "the SetParameters actor message" should {
    "refuse zero parameters" in {
      Given(s"zero parameters")
      assert(Try(SetParameters(Map.empty)).isFailure)
    }

    "refuse too many parameters" in {
      Given(s"more than $maxParameterCount parameters")
      assert(Try(SetParameters(tooManyParams)).isFailure)
    }

    s"refuse parameter names longer than $maxParameterNameLength characters" in {
      Given(s"parameter names longer than $maxParameterNameLength characters")
      val params = oneParamOfEachType.map { case (name, pv) => name * 10 -> pv }
      assert(Try(SetParameters(params)).isFailure)
    }
  }

  "the SetParameters operation actor" should {
    "return a future that eventually contains the result of the operation" in
      withBasicResponder(oneParamOfEachType) {
        withSetParameters { setActor =>
          withGetNamedParameters { getActor =>
            val params = Map(
              "FLOAT_4" -> 7.7f,
              "BYTE_6" -> 11.toByte,
              "INT_10" -> 999
            )

            setParameters(params, setActor)

            val future2 = (getActor ? GetNamedParameters(params.keySet)).mapTo[GetNamedParametersResult]
            Await.result(future2, 15.seconds)

            val Success(result2: GetNamedParametersResult) = future2.value.get
            result2.params shouldBe params
          }
        }
      }

    "handle a large number of parameters" in
      withBasicResponder(maxParams) {
        withSetParameters { actor =>
          val params = generateSubset(maxParams)
          setParameters(params, actor)
        }
      }

    "fail to set a non-existent parameter" in
      withBasicResponder(oneParamOfEachType) {
        withSetParameters { actor =>
          Given("sending a SetParameters message to the actor")
          val paramsExist = Map(
            "FLOAT_4" -> 9.9f,
            "BYTE_6" -> 22.toByte
          )

          val paramsNotExist = Map(
            "qqq" -> 999,
            "rrr" -> 888L
          )

          val paramsExpect = Map(
            "FLOAT_4" -> 9.9f,
            "BYTE_6" -> 22f
          )

          setSomeParameters(paramsExist ++ paramsNotExist, paramsExpect, actor)
        }
      }

    "return empty result if all set parameters do not exist" in
      withBasicResponder(oneParamOfEachType) {
        withSetParameters { actor =>
          Given("sending a SetParameters message to the actor")
          val paramsNotExist = Map(
            "qqq" -> 999,
            "rrr" -> 888L
          )

          setSomeParameters(paramsNotExist, Map.empty, actor)
        }
      }

    "handle duplicate packets" in
      withDuplicatingResponder(params32767, 10) {
        withSetParameters { actor =>
          val params = generateSubset(params32767)
          setParameters(params, actor)
        }
      }

    "handle dropped packets" in
      withDroppingResponder(params32767, 10) {
        withSetParameters { actor =>
          val params = generateSubset(params32767)
          setParameters(params32767, actor)
        }
      }

    "handle delayed packets" in
      withDelayingResponder(params32767, 10, 3.seconds) {
        withSetParameters { actor =>
          val params = generateSubset(params32767)
          setParameters(params32767, actor)
        }
      }
  }
}
