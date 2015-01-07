package scavlink.link.nav

import org.scalatest.{Matchers, WordSpec}
import scavlink.coord.Formulas._
import scavlink.coord.Geo
import scavlink.link.mission.MissionTestData
import scavlink.message.{SystemId, VehicleId}
import scavlink.state.{LocationState, MissionState}

class TrackMissionSpec extends WordSpec with Matchers with MissionTestData {
  val vehicle = VehicleId.fromLink("spec", SystemId(1))
  val course = TrackMission(toMission(toCommands(waypoints.take(3))))
  val courseMidpoint = midpoint(waypoints(0), waypoints(1))

  // Geo value goes through MissionItem (Float) and back to Geo (Double)
  def floatConv(p: Geo): Geo = Geo(p.x.toFloat.toDouble, p.y.toFloat.toDouble, p.z.toFloat.toDouble)

  "the FollowMission course" should {
    "extract only navigational mission items" in {
      course.waypoints shouldBe waypoints.take(3).zipWithIndex.map {
        case (p, i) => i -> floatConv(p)
      }.toMap
    }

    "set isCompleted to true on completed()" in {
      assert(!course.isComplete)
      assert(course.completed.isComplete)
    }

    "set isArrived when location matches or doesn't match waypoint" in {
      assert(!course.isArrived)
      val nc = course.update(LocationState(vehicle, location = waypoints(0))).asInstanceOf[TrackMission]
      assert(nc.isArrived)
      val nc2 = nc.update(LocationState(vehicle, location = courseMidpoint)).asInstanceOf[TrackMission]
      assert(!nc2.isArrived)
    }

    "change waypoint when mission index is updated" in {
      course.waypoint shouldBe Some(floatConv(waypoints(0)))
      val nc = course.update(MissionState(vehicle, currentIndex = 1))
      nc.waypoint shouldBe Some(floatConv(waypoints(1)))
    }

    "compute distance for new location" in {
      val mid = midpoint(waypoints(0), waypoints(1))
      assert(course.distance.isNaN)
      val nc = course.update(LocationState(vehicle, timeIndex = 5, location = courseMidpoint)).asInstanceOf[TrackMission]
      
      val d: Double = 190.8956977402025
      nc.distance shouldBe d
      nc.distances shouldBe Vector((5L, d))
    }
  }
}
