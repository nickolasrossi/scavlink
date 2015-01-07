package scavlink.test.map

import scavlink.coord.{ENU, Geo}
import scavlink.message.Command
import scavlink.message.common.NavWaypoint

import scala.annotation.tailrec
import scala.math._

abstract class PolygonMission(start: Geo, length: Int, sides: Int) extends MissionValue {
  val mission: Vector[Command] = {
    val polygon = Polygon.make(start, length, sides)
    NavWaypoint(start) +: polygon.map(NavWaypoint.apply) :+ NavWaypoint(start)
  }
}

abstract class PolygonGuided(start: Geo, length: Int, sides: Int) extends GuidedValue {
  val points: Seq[Geo] = Polygon.make(start, length, sides)
}

object Polygon {
  def make(start: Geo, length: Double, sides: Int): Vector[Geo] = {
    val angle = 360D / sides.toDouble

    @tailrec
    def rec(prev: Geo, h: Double, s: Int, acc: Vector[Geo]): Vector[Geo] = {
      if (s < 0) {
        acc
      } else {
        val loc = prev.move(length, h)
        rec(loc, h - angle, s - 1, acc :+ loc)
      }
    }

    rec(start, 0, sides, Vector.empty)
  }
}

abstract class CorkscrewMission(start: Geo) extends MissionValue {
  val mission: Vector[Command] = Corkscrew.make(start, .5, .1).map(NavWaypoint.apply)
}

object Corkscrew {
  def make(start: Geo, coilRate: Double, heightRate: Double): Vector[Geo] = {
    (1 to 30).map {
      i =>
        val t = i.toDouble
        start + ENU(t * cos(t * coilRate), t * sin(t * coilRate), i.toDouble * heightRate)
    }.toVector
  }
}