package scavlink.link.mission

import java.io.{File, PrintWriter, Writer}

import scavlink.message.common.MissionItem
import scavlink.message.{ComponentId, SystemId, Unsigned}

import scala.io.Source

/**
 * Reads and writes a mission in the qgroundcontrol waypoint file format.
 * @see [[http://qgroundcontrol.org/mavlink/waypoint_protocol#waypoint_file_format]]
 * @author Nick Rossi
 */
object WaypointFile {
  val FileHeader = "QGC WPL 110"

  /**
   * Write a mission to a waypoint file.
   */
  def writeFile(file: File, mission: Mission) = write(new PrintWriter(file), mission)

  /**
   * Read a mission from a waypoint file.
   */
  def readFile(file: File): Mission = read(Source.fromFile(file))

  /**
   * Write a mission to waypoint file format.
   */
  def write(writer: Writer, mission: Mission): Unit = {
    val pw = writer match {
      case w: PrintWriter => w
      case w => new PrintWriter(w)
    }

    try {
      pw.println(FileHeader)
      mission.map(missionItemToLine).foreach(pw.println)
    } finally {
      pw.close()
    }
  }

  /**
   * Read a mission from waypoint file format.
   */
  def read(source: Source): Mission = {
    val lineIter = source.getLines()
    if (lineIter.next().startsWith(FileHeader)) {
      lineIter.map(lineToMissionItem).toVector.sortBy { item => Unsigned(item.seq) }
    } else {
      Vector.empty
    }
  }


  def missionItemToLine(item: MissionItem): String =
    s"${ Unsigned(item.seq) }\t${ Unsigned(item.current) }\t${ Unsigned(item.frame) }\t${ Unsigned(item.command) }\t" +
    s"${ item.param1 }\t${ item.param2 }\t${ item.param3 }\t${ item.param4 }\t${ item.x }\t${ item.y }\t${ item.z }\t" +
    s"${ Unsigned(item.autocontinue) }"

  def lineToMissionItem(line: String): MissionItem = {
    // default all values to zero but autocontinue to 1
    val parts = line.split('\t').padTo(11, "0").padTo(12, "1")

    MissionItem(
      SystemId.zero,
      ComponentId.zero,
      parts(0).toShort, // seq
      parts(2).toByte, // frame
      parts(3).toShort, // command
      parts(1).toByte, // current
      parts(11).toByte, // autocontinue
      parts(4).toFloat, // param1
      parts(5).toFloat, // param2
      parts(6).toFloat, // param3
      parts(7).toFloat, // param4
      parts(8).toFloat, // x
      parts(9).toFloat, // y
      parts(10).toFloat // z
    )
  }
}
