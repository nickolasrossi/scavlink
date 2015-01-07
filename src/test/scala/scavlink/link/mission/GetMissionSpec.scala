package scavlink.link.mission

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import scavlink.link.operation.OpTimeout
import scavlink.message.{ComponentId, SystemId}

import scala.concurrent.Await
import scala.concurrent.duration._

class GetMissionSpec(_system: ActorSystem) extends MissionOpSpec(_system) {

  def this() = this(ActorSystem("GetMissionSpec"))

  def getMission(resultSet: Mission, actor: ActorRef, duration: Duration = 15.seconds): Mission = {
    Given("sending a GetMission message to the actor")
    val future1 = (actor ? GetMission()).mapTo[GetMissionResult]
    val result1 = Await.result(future1, duration)

    Then("the GetMission result arrives")
    result1.mission shouldBe resultSet.map(_.setTarget(SystemId.GroundControl, ComponentId.GroundControl))
    result1.mission
  }


  // tests

  "The GetMission actor" should {
    "return a future that eventually contains the current mission" in
      withBasicResponder(simulatedSunnyvaleMission) {
        withGetMission { actor =>
          getMission(simulatedSunnyvaleMission, actor)
        }
      }

    "handle 1000 mission items" in
      withBasicResponder(mission1000) {
        withGetMission { actor =>
          getMission(mission1000, actor)
        }
      }

    "handle 32767 mission items (just before Short type goes negative)" in
      withBasicResponder(mission32767) {
        withGetMission { actor =>
          getMission(mission32767, actor)
        }
      }

    "handle 65535 mission items (max value for mission count)" in
      withBasicResponder(maxMission) {
        withGetMission { actor =>
          getMission(maxMission, actor)
        }
      }

    "handle duplicate packets" in
      withDuplicatingResponder(mission150, 5) {
        withGetMission { actor =>
          getMission(mission150, actor)
        }
      }

    "handle delayed packets" in
      withDelayingResponder(mission150, 50, 3.seconds) {
        withGetMission { actor =>
          getMission(mission150, actor, 10.seconds)
        }
      }

    "handle dropped packets" in
      withDroppingResponder(mission150, 50) {
        withGetMission { actor =>
          getMission(mission150, actor, 10.seconds)
        }
      }

    "time out properly when the connection stops responding " in
      withDyingResponder(mission150, 10) {
        withGetMission { actor =>
          Given("sending a GetMission message to a dying connection")
          val op = GetMission()
          val future1 = (actor ? op).mapTo[GetMissionResult]

          Then("the future should return an OpTimeout failure")
          an [OpTimeout] should be thrownBy Await.result(future1, 15.seconds)
        }
      }
  }
}
