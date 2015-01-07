package scavlink.link.mission

import scavlink.link.operation.{Op, OpResult}

trait MissionOp extends Op

trait MissionOpResult extends OpResult {
  def op: MissionOp
  def mission: Mission
}
