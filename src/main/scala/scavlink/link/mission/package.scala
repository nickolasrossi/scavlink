package scavlink.link

import scavlink.coord.Geo
import scavlink.message.Command
import scavlink.message.common.{MissionItem, NavWaypoint}

import scala.language.implicitConversions

package object mission {
  type Mission = Vector[MissionItem]
  type PartialMission = scala.collection.mutable.Seq[Option[MissionItem]]

  val MaxMissionItems = 65534

  implicit def commandsToMission(commands: Vector[Command]): Vector[MissionItem] =
    commands.zipWithIndex.map { case (c, i) => c.toMissionItem(i) }

  def pointsToMission(waypoints: Vector[Geo]): Vector[MissionItem] =
    waypoints.zipWithIndex.map { case (p, i) => NavWaypoint(location = p).toMissionItem(i) }
}
