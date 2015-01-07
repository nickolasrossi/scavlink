package scavlink.link.command

import scavlink.link.command.CommandTestData.CommandResponses
import scavlink.message.enums.{MavCmd, MavResult}

import scala.concurrent.duration._

object CommandTestData {
  type CommandResponses = Map[MavCmd.Value, (FiniteDuration, MavResult.Value)]

}

trait CommandTestData {
  // predefined results for various commands

  val navResponses: CommandResponses = Map[MavCmd.Value, (FiniteDuration, MavResult.Value)](
    MavCmd.COMPONENT_ARM_DISARM -> (2.seconds, MavResult.ACCEPTED),
    MavCmd.NAV_WAYPOINT -> (0.seconds, MavResult.ACCEPTED),
    MavCmd.NAV_RETURN_TO_LAUNCH -> (0.seconds, MavResult.ACCEPTED),
    MavCmd.DO_FENCE_ENABLE -> (3.seconds, MavResult.TEMPORARILY_REJECTED),
    MavCmd.DO_JUMP -> (0.seconds, MavResult.DENIED),
    MavCmd.DO_PARACHUTE -> (10.seconds, MavResult.FAILED)
  ).withDefaultValue((0.seconds, MavResult.UNSUPPORTED))
}
