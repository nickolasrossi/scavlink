package scavlink.link.parameter

import scavlink.link.operation.{Op, OpResult}

trait ParameterOp extends Op

trait ParameterOpResult extends OpResult {
  def op: ParameterOp
  def params: Parameters
  def isAll: Boolean
}
