package scavlink.link.nav

import scavlink.coord.Coordinates
import scavlink.link.Vehicle
import scavlink.link.nav.NavOps._
import scavlink.link.operation.{Conversation, ConversationStep, Op}
import scavlink.message.Mode
import scavlink.message.common._
import scavlink.message.enums._

trait NavOp extends Op

case class ArmMotors(shouldArm: Boolean) extends Conversation(armMotors(shouldArm)) with NavOp

case class SetVehicleMode(vehicle: Vehicle, mode: Mode)
  extends Conversation(setMode(vehicle, mode)) with NavOp

case class SetVehicleModeToHeartbeat(vehicle: Vehicle, mode: Mode)
  extends Conversation(setModeToHeartbeat(vehicle, mode)) with NavOp


object NavOps {
  /**
   * Conversation step that sets the vehicle-independent mode.
   */
  def setMode(vehicle: Vehicle, mode: Mode): ConversationStep =
    setMode(MavModeFlag.CUSTOM_MODE_ENABLED.id, mode(vehicle.info.vehicleType))

  /**
   * Conversation step that sets the base and custom mode by raw id.
   */
  def setMode(baseModeId: Int, customModeId: Int): ConversationStep =
    ConversationStep(SetMode(baseMode = baseModeId.toByte, customMode = customModeId)) {
      case CommandAck(cmd, result) if cmd == MavCmd.ACK_SET_MODE => result == MavResult.ACCEPTED.id
    }

  /**
   * Conversation step that sets the mode, and waits for the heartbeat
   * to reflect the new mode. A little delay involved, but it guarantees
   * that other actors are notified of the updated mode before proceeding.
   */
  def setModeToHeartbeat(vehicle: Vehicle, mode: Mode): ConversationStep =
    setModeToHeartbeat(MavModeFlag.CUSTOM_MODE_ENABLED.id, mode(vehicle.info.vehicleType))

  def setModeToHeartbeat(baseModeId: Int, customModeId: Int): ConversationStep =
    ConversationStep(SetMode(baseMode = baseModeId.toByte, customMode = customModeId)) {
      case msg: Heartbeat if msg.customMode == customModeId => true
    }

  /**
   * Conversation step that arms or disarms the vehicle motors.
   */
  def armMotors(shouldArm: Boolean): ConversationStep =
    ConversationStep(ComponentArmDisarm(0, MavComponent.SYSTEM_CONTROL, if (shouldArm) 1 else 0))

  /**
   * Conversation step that sets a guided mode destination.
   */
  def guidedPoint(vehicle: Vehicle, location: Coordinates): ConversationStep = {
    val missionItem = NavWaypoint(location).toMissionItem(index = 0, autocontinue = false, current = 2)
    val mySystemId = vehicle.link.config.heartbeat.thisSystemId
    ConversationStep(missionItem) {
      case m@MissionAck(`mySystemId`, _, result) => result == MavMissionResult.ACCEPTED
    }
  }
}
