package scavlink.link.mission

import scavlink.coord.Geo
import scavlink.message.common.{MissionItem, NavReturnToLaunch, NavWaypoint}
import scavlink.message.{Command, ComponentId, SystemId}

trait MissionTestData {
  val missionFromFile = Vector[MissionItem](
    MissionItem(SystemId.zero, ComponentId.zero, 0, 0, 16, 1, 1, 0, 0, 0, 0, -35.36288f, 149.16522f, 582f),
    MissionItem(SystemId.zero, ComponentId.zero, 1, 3, 22, 0, 1, 0, 0, 0, 0, -35.36288f, 149.16522f, 20f),
    MissionItem(SystemId.zero, ComponentId.zero, 2, 3, 16, 0, 1, 0, 3, 0, 0, -35.36465f, 149.1635f, 20f),
    MissionItem(SystemId.zero, ComponentId.zero, 3, 3, 115, 0, 1, 640, 20, 1, 1, 0, 0, 0),
    MissionItem(SystemId.zero, ComponentId.zero, 4, 3, 19, 0, 1, 5, 0, 0, 1, 0, 0, 20),
    MissionItem(SystemId.zero, ComponentId.zero, 5, 3, 16, 0, 1, 0, 3, 0, 0, -35.36536f, 149.1635f, 20f),
    MissionItem(SystemId.zero, ComponentId.zero, 6, 3, 16, 0, 1, 1, 0, 0, 0, -35.36536f, 149.164f, 40f),
    MissionItem(SystemId.zero, ComponentId.zero, 7, 3, 16, 0, 1, 0, 3, 0, 0, -35.36536f, 149.16457f, 20f),
    MissionItem(SystemId.zero, ComponentId.zero, 8, 3, 114, 0, 1, 100, 0, 0, 0, 0, 0, 0),
    MissionItem(SystemId.zero, ComponentId.zero, 9, 3, 113, 0, 1, 0, 0, 0, 0, 0, 0, 40),
    MissionItem(SystemId.zero, ComponentId.zero, 10, 3, 16, 0, 1, 0, 3, 0, 0, -35.36465f, 149.16454f, 20f),
    MissionItem(SystemId.zero, ComponentId.zero, 11, 3, 16, 0, 1, 0, 3, 0, 0, -35.36465f, 149.164f, 20f),
    MissionItem(SystemId.zero, ComponentId.zero, 12, 3, 177, 0, 1, 10, 1, 0, 0, 0, 0, 0),
    MissionItem(SystemId.zero, ComponentId.zero, 13, 3, 20, 0, 1, 0, 0, 0, 0, 0, 0, 20)
  )


  val waypoints = Vector(
    Geo(37.411848, -121.995104, 20),
    Geo(37.415248, -121.994568, 20),
    Geo(37.414762, -121.989364, 20),
    Geo(37.413697, -121.987712, 20),
    Geo(37.412103, -121.989493, 20),
    Geo(37.412572, -121.991489, 20),
    Geo(37.411848, -121.995104, 20)
  )

  val simulatedSunnyvaleMission = toMission(toCommands(waypoints))

  val mission20 = generateMission(20)

  val mission150 = generateMission(150)

  val mission1000 = generateMission(1000)

  val mission32767 = generateMission(32767)

  val maxMission = generateMission(MaxMissionItems)

  def generateMission(items: Int): Mission = toMission(
    for (i <- 0 until items) yield NavWaypoint(Geo(37 + ((items - i).toFloat / 100000), -122, 80))
  )

  def toCommands(points: Seq[Geo]): Seq[Command] = points.map(NavWaypoint.apply) :+ NavReturnToLaunch()

  def toMission(commands: Seq[Command]) = commands.zipWithIndex.map { case (cmd, i) => cmd.toMissionItem(i) }.toVector
}
