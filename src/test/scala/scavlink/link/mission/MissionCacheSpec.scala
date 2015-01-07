package scavlink.link.mission

import akka.actor.ActorSystem
import akka.testkit.TestProbe
import scavlink.link.mission.MissionAskAPI._
import scavlink.link.{SentPacket, SubscribeTo}
import scavlink.message.common.{MissionCount, MissionRequest, MissionWritePartialList}
import scavlink.message.enums.MavComponent
import scavlink.message.{VehicleId, ComponentId, Packet, SystemId}

import scala.concurrent.Await
import scala.concurrent.duration._

class MissionCacheSpec(_system: ActorSystem) extends MissionOpSpec(_system) {
  val duration = 15.seconds

  def this() = this(ActorSystem("MissionCacheSpec"))

  "the MissionCache" should {
    "return cached result on the second GetMission request" in
      withBasicResponder(mission20) {
        val localProbe = new TestProbe(system)
        link.events.subscribe(localProbe.ref, SubscribeTo.message(classOf[MissionCount]))

        val future1 = vehicle.getMission
        val result1 = Await.result(future1, duration)
        result1.mission shouldBe mission20.map(setGCS)

        val future2 = vehicle.getMission
        val result2 = Await.result(future2, duration)
        result2.mission shouldBe mission20.map(setGCS)

        // expect the first MissionCount, but not another one
        localProbe.expectMsgClass(classOf[Packet])
        localProbe.expectNoMsg()

        // after clearing cache, expect MissionCount again
        vehicle.clearMissionCache()

        val future3 = vehicle.getMission
        val result3 = Await.result(future3, duration)
        result3.mission shouldBe mission20.map(setGCS)

        localProbe.expectMsgClass(classOf[Packet])
      }

    "return cached result for GetMission after a SetMission" in
      withBasicResponder(Vector.empty) {
        val localProbe = new TestProbe(system)
        link.events.subscribe(localProbe.ref, SubscribeTo.message(classOf[MissionCount]))

        val future1 = vehicle.setMission(mission20)
        val result1 = Await.result(future1, duration)
        result1.mission shouldBe mission20.map(setGCS)

        val future2 = vehicle.getMission
        val result2 = Await.result(future2, duration)
        result2.mission shouldBe mission20.map(setGCS)

        // expect no MissionCount
        localProbe.expectNoMsg()
      }

    "serialize two SetMission operations" in {
      withBasicResponder(Vector.empty) {
        val future1 = vehicle.setMission(mission1000)
        val future2 = vehicle.setMission(mission20)

        val result1 = Await.result(future1, duration)
        result1.mission shouldBe mission1000.map(setGCS)

        val result2 = Await.result(future2, duration)
        result2.mission shouldBe mission20.map(setGCS)
      }
    }

    "notify cache eviction when unexpected MissionItem message is detected" in
      withBasicResponder(mission20) {
        val localProbe = new TestProbe(system)
        link.events.subscribe(localProbe.ref, SubscribeTo.events(classOf[ReceivedMission], classOf[ExternalMissionUpdate]))

        val future1 = vehicle.getMission
        val result1 = Await.result(future1, duration)
        result1.mission shouldBe mission20.map(setGCS)
        localProbe.expectMsg(duration, ReceivedMission(vehicle, mission20.map(setGCS)))

        // simulate a MissionItem from another source
        val item = mission20(5).copy(param1 = 99)
        link.events.publish(Packet(id, systemId, componentId, setGCS(item)))

        localProbe.expectMsg(duration, ExternalMissionUpdate(vehicle))
      }

    "notify cache eviction when unexpected MissionRequest message is detected" in
      withBasicResponder(mission20) {
        val localProbe = new TestProbe(system)
        link.events.subscribe(localProbe.ref, SubscribeTo.events(classOf[ReceivedMission], classOf[ExternalMissionUpdate]))

        link.events.publish(Packet(id, systemId, componentId, MissionRequest(systemId, ComponentId(1), 4)))

        localProbe.expectMsg(duration, ExternalMissionUpdate(vehicle))
      }

    "convert SetMission to partial when new mission overlaps old" in
      withBasicResponder(mission20) {
        val future1 = vehicle.getMission
        Await.result(future1, duration)

        val localProbe = new TestProbe(system)
        link.events.subscribe(localProbe.ref, SubscribeTo.complete {
          case SentPacket(Packet(_, msg: MissionWritePartialList)) => true
        })

        val newMission = mission20.take(10) ++ mission1000.slice(10, 14) ++ mission20.takeRight(6)
        val future2 = vehicle.setMission(newMission)
        val result2 = Await.result(future2, duration)

        localProbe.expectMsg(SentPacket(Packet(
          VehicleId.GroundControl, SystemId.GroundControl, ComponentId.GroundControl,
          MissionWritePartialList(systemId, MavComponent.MISSIONPLANNER, 10, 14))))
      }
  }
}
