package scavlink.link.operation

import akka.actor.Actor

trait Op {
  val name: String = this.getClass.getSimpleName
  def actorType: Class[_ <: Actor]
}

trait OpProgress {
  def op: Op
  def percent: Int
}

trait OpResult {
  def op: Op
}

trait OpException extends Exception with OpResult {
  override def toString = s"${getClass.getSimpleName}($op)"
}

case class Queued(op: Op) extends OpProgress { val percent = -1 }
case class Started(op: Op) extends OpProgress { val percent = 0 }
case class Joined(op: Op) extends OpProgress { val percent = 0 }


/**
 * Cancels an operation.
 */
case class Cancel(op: Op)
/**
 * Cancels an operation by context.
 */
case class CancelContext(ctx: Any)