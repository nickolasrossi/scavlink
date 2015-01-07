package scavlink.link.nav

import org.scalatest.{Matchers, WordSpec}
import scavlink.coord.NED
import scavlink.link.mission.MissionTestData
import scavlink.message.{SystemId, VehicleId}
import scavlink.state.LocationState

import scala.concurrent.duration._

class GotoLocationsSpec extends WordSpec with Matchers with MissionTestData {
  val vehicle = VehicleId.fromLink("spec", SystemId(1))
  val course = GotoLocations(waypoints, hasArrived = withinMeters(1, 1), maxEta = 1.hour, smoothingWindow = 5.seconds)

  val points = Vector(
    waypoints(0) + NED(-30, -30, 40),
    waypoints(0) + NED(-29.8, -29, 40),
    waypoints(0) + NED(-29.8, -28, 39),
    waypoints(0) + NED(-29.5, -26, 37),
    waypoints(0) + NED(-29.4, -25.7, 34),
    waypoints(0) + NED(-29.2, -23, 31),
    waypoints(0) + NED(-28.7, -20, 28),
    waypoints(0) + NED(-25.3, -14, 23),
    waypoints(0) + NED(-22.1, -9, 17),
    waypoints(0) + NED(-18.3, -6, 13.4)
  )

  val telemetry = points.zipWithIndex.map { case (p, i) =>
    LocationState(vehicle, timeIndex = i * 1000, location = p)
  }


  "the GotoLocations course" should {
    "update waypoint to the next location upon arrival at current waypoint" in {
      course.waypoint shouldBe waypoints(0)

      val newCourse = course.update(LocationState(vehicle, location = waypoints(0)))
      newCourse.waypoint shouldBe waypoints(1)
    }

    "update distance value and distances vector" in {
      def updateAndCheckCourse(course: GotoLocations, index: Int): Unit = {
        course.update(telemetry(index)) match {
          case nc: GotoLocations =>
            println(s"index=$index dist=${nc.distance} dc=${nc.distanceChangeRate} wp=${waypoints(0)}")
            println(s"last=${course.current} this=${nc.current}")
            println(nc.distances)

            nc.current shouldBe Some(points(index))

            val distance = points(index).haversineDistance(waypoints(0))
            nc.distance shouldBe distance
            nc.distances.last shouldBe((index * 1000).toLong, distance)

            if (!course.distance.isNaN) {
              assert(nc.distance < course.distance)
            }

            // at this point in the telemetry, we should start seeing a distance change calculation
            if (index > 5) {
              assert(nc.distanceChangeRate < 0)
            }

            nc.status shouldBe CourseStatus.OK

            if (index < telemetry.length - 1) {
              updateAndCheckCourse(nc, index + 1)
            }

          case _ => fail()
        }
      }

      updateAndCheckCourse(course, 0)
    }

    "not add a new time/distance sample if it's identical to the last one" in {
      val newCourse = course.update(telemetry(0))
      newCourse.update(telemetry(0)) match {
        case nc: GotoLocations =>
          nc.distances.length shouldBe 1

        case _ => fail()
      }
    }

    "flag the course as Error when LocationStates send it away from waypoint" in {
      val telemetry = points.zipWithIndex.map { case (p, i) =>
        LocationState(vehicle, timeIndex = (points.length - i - 1) * 1000, location = p)
      }

      def updateAndCheckCourse(course: GotoLocations, index: Int): Unit = {
        course.update(telemetry(index)) match {
          case nc: GotoLocations =>
            println(s"index=$index dist=${nc.distance} dc=${nc.distanceChangeRate} wp=${waypoints(0)}")
            println(s"last=${course.current} this=${nc.current}")
            println(nc.distances)

            if (!course.distance.isNaN) {
              assert(nc.distance > course.distance)
            }

            if (index < 5) {
              assert(nc.distanceChangeRate > 0)
              nc.status shouldBe CourseStatus.Error
            } else {
              nc.status shouldBe CourseStatus.OK
            }

            if (index > 0) {
              updateAndCheckCourse(nc, index - 1)
            }

          case _ => //
        }
      }

      updateAndCheckCourse(course, telemetry.length - 1)
    }

    "flag the course as Error when LocationStates indicate extremely slow progress" in {
      val telemetry = (0 to 9).map { i =>
        LocationState(vehicle, timeIndex = i * 1000, location = points(0))
      }

      def updateAndCheckCourse(course: GotoLocations, index: Int): Unit = {
        course.update(telemetry(index)) match {
          case nc: GotoLocations =>
            println(s"index=$index dist=${nc.distance} dc=${nc.distanceChangeRate} wp=${waypoints(0)}")
            println(s"last=${course.current} this=${nc.current}")
            println(nc.distances)

            if (index > 4) {
              nc.status shouldBe CourseStatus.Error
            } else {
              nc.status shouldBe CourseStatus.OK
            }

            if (index < telemetry.length - 1) {
              updateAndCheckCourse(nc, index + 1)
            }

          case _ => //
        }
      }

      updateAndCheckCourse(course, 0)
    }

    "flag the course as Warning when LocationStates indicate moderately slow progress" ignore {
      // spread the telemetry samples over > 30 minutes
      val telemetry = points.zipWithIndex.map { case (p, i) =>
        LocationState(vehicle, timeIndex = i * 1000 * 2000, location = p)
      }

      def updateAndCheckCourse(course: GotoLocations, index: Int): Unit = {
        course.update(telemetry(index)) match {
          case nc: GotoLocations =>
            println(s"index=$index dist=${nc.distance} dc=${nc.distanceChangeRate} wp=${waypoints(0)}")
            println(s"last=${course.current} this=${nc.current}")
            println(nc.distances)

            if (index > 9) {
              nc.status shouldBe CourseStatus.Error
            } else {
              nc.status shouldBe CourseStatus.OK
            }

            if (index < telemetry.length - 1) {
              updateAndCheckCourse(nc, index + 1)
            }

          case _ => //
        }
      }

      updateAndCheckCourse(course, 0)
    }

    "flag the course as Error when current location moves outside the fence" ignore {
//      val initCourse = course.copy(fence )
    }
  }
}
