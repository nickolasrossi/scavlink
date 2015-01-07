package scavlink.link.mission

import java.io.StringWriter

import org.scalatest.{Matchers, WordSpec}
import scavlink.link.mission.WaypointFile._

import scala.io.Source

class WaypointFileSpec extends WordSpec with Matchers with MissionTestData {
  "readMission" should {
    "parse a QGC text file into a valid Mission" in {
      val source = Source.fromInputStream(getClass.getResourceAsStream("/copter_mission.txt"))
      val mission = read(source)

      mission.zip(missionFromFile) foreach { case (item1, item2) =>
        item1 shouldBe item2
      }
    }

    "return empty mission if the file is missing a valid first line" in {
      val source = Source.fromString("QQQ\nRRR\n")
      val mission = read(source)
      mission shouldBe Vector.empty
    }
  }

  "writeMission" should {
    "write a mission that matches the original file" in {
      val source = Source.fromInputStream(getClass.getResourceAsStream("/copter_mission.txt"))
      val expected = source.mkString

      assertResult(expected) {
        val writer = new StringWriter
        write(writer, missionFromFile)
        writer.toString
      }
    }
  }
}
