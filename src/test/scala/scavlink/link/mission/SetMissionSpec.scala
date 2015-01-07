package scavlink.link.mission

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import scavlink.link.operation.OpTimeout
import scavlink.message.common.{MissionItem, NavWaypoint}
import scavlink.message.{ComponentId, SystemId, Unsigned}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

class SetMissionSpec(_system: ActorSystem) extends MissionOpSpec(_system) {

  def this() = this(ActorSystem("SetMissionSpec"))

  def setMission(mission: Mission, setActor: ActorRef, getActor: ActorRef, duration: Duration = 15.seconds): Mission = {
    Given("sending a SetMission message to the actor")
    val future1 = (setActor ? SetMission(mission)).mapTo[SetMissionResult]
    val result1 = Await.result(future1, duration)

    Then("the DidSetParameters result contains the newly set mission")
    result1.mission shouldBe mission.map(setGCS)

    val future2 = (getActor ? GetMission()).mapTo[GetMissionResult]
    val result2 = Await.result(future2, duration)

    And("GetMission should return the newly set mission")
    result2.mission shouldBe mission.map(setGCS)

    result1.mission
  }

  def setPartialMission(mission: Mission, original: Mission, setActor: ActorRef, getActor: ActorRef, duration: Duration = 15.seconds): Mission = {
    Given("sending a SetMission message to the actor")
    val future1 = (setActor ? SetMission(mission, true)).mapTo[SetMissionResult]
    val result1 = Await.result(future1, duration)

    Then("the DidSetParameters result contains the newly set mission")
    result1.mission.foreach(println)
    result1.mission shouldBe mission.map(setGCS)

    val future2 = (getActor ? GetMission()).mapTo[GetMissionResult]
    val result2 = Await.result(future2, duration)

    val start = Unsigned(mission.head.seq)
    val end = Unsigned(mission.last.seq)
    val expectedMission = Vector.tabulate[MissionItem](original.length) { i =>
      val item = if (i >= start && i <= end) mission(i - start) else original(i)
      item.setTarget(SystemId.GroundControl, ComponentId.GroundControl)
    }

    And("GetMission should return the newly set mission")
    result2.mission.foreach(println)
    result2.mission shouldBe expectedMission

    result1.mission
  }

  
  // tests

  "the SetMission message" should {
    "require items to be in sequence" in {
      val mission = waypoints.map(p => NavWaypoint(location = p).toMissionItem(1))
      assert(Try(SetMission(mission)).isFailure)
    }

    "allow partial mission with a start above zero" in {
      val mission = waypoints.zipWithIndex.map { case (p, i) => NavWaypoint(location = p).toMissionItem(i + 3) }
      assert(Try(SetMission(mission)).isFailure)
      assert(Try(SetMission(mission, true)).isSuccess)
    }
  }

  "the SetMission actor" should {
    "return a future that eventually contains the new mission" in
      withBasicResponder(emptyMission) {
        withSetMission { setActor =>
          withGetMission { getActor =>
            setMission(simulatedSunnyvaleMission, setActor, getActor)
          }
        }
      }

    "handle 1000 mission items" in
      withBasicResponder(emptyMission) {
        withSetMission { setActor =>
          withGetMission { getActor =>
            setMission(mission1000, setActor, getActor)
          }
        }
      }

    "handle 32767 mission items (just before Short type goes negative)" in
      withBasicResponder(emptyMission) {
        withSetMission { setActor =>
          withGetMission { getActor =>
            setMission(mission32767, setActor, getActor, 30.seconds)
          }
        }
      }

    "handle 65535 mission items (max value for mission count)" in
      withBasicResponder(emptyMission) {
        withSetMission { setActor =>
          withGetMission { getActor =>
            setMission(maxMission, setActor, getActor, 30.seconds)
          }
        }
      }

    "handle a partial mission with items in the middle of the list" in {
      withBasicResponder(mission20) {
        withSetMission { setActor =>
          withGetMission { getActor =>
            setPartialMission(mission150.slice(5, 12), mission20, setActor, getActor, 30.seconds)
          }
        }
      }
    }

    "handle a partial mission with items at the start of the list" in {
      withBasicResponder(mission20) {
        withSetMission { setActor =>
          withGetMission { getActor =>
            setPartialMission(mission150.slice(0, 5), mission20, setActor, getActor, 30.seconds)
          }
        }
      }
    }

    "handle a partial mission with items at the end of the list" in {
      withBasicResponder(mission20) {
        withSetMission { setActor =>
          withGetMission { getActor =>
            setPartialMission(mission150.slice(11, 20), mission20, setActor, getActor, 30.seconds)
          }
        }
      }
    }

    "handle duplicate packets" in
      withDuplicatingResponder(emptyMission, 5) {
        withSetMission { setActor =>
          withGetMission { getActor =>
            setMission(mission20, setActor, getActor)
          }
        }
      }

    "handle delayed packets" in
      withDelayingResponder(emptyMission, 9, 3.seconds) {
        withSetMission { setActor =>
          withGetMission { getActor =>
            setMission(mission20, setActor, getActor)
          }
        }
      }

    "handle dropped packets" in
      withDroppingResponder(emptyMission, 9) {
        withSetMission { setActor =>
          withGetMission { getActor =>
            setMission(mission20, setActor, getActor)
          }
        }
      }

    "time out properly when the connection stops responding " in
      withDyingResponder(emptyMission, 10) {
        withSetMission { actor =>
          Given("sending a GetMission message to a dying connection")
          val op = SetMission(mission150)
          val future1 = (actor ? op).mapTo[SetMissionResult]

          Then("the future should return an OpTimeout failure")
          an [OpTimeout] should be thrownBy Await.result(future1, 15.seconds)
        }
      }
  }
}
