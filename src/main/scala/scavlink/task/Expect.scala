package scavlink.task

import akka.actor.Status.Failure
import scavlink.link.operation._

/**
 * Partial functions that convert received actor messages into
 * TaskProgress or TaskResult objects for update on a websocket session.
 * @author Nick Rossi
 */
trait Expect {
  def progress: PartialFunction[Any, TaskProgress]
  def result: PartialFunction[Any, TaskComplete]
  def failure: PartialFunction[Any, TaskComplete]
}

/**
 * Implementation for messages received from [[OpSupervisor]].
 */
trait ExpectOp extends Expect {
  final def progress: PartialFunction[Any, TaskProgress] = {
    case p: OpProgress => opProgress.applyOrElse(p, defaultProgress)
  }

  final def result: PartialFunction[Any, TaskComplete] = {
    case (r: OpResult) => opResult.applyOrElse(r, defaultResult)
  }

  final def failure: PartialFunction[Any, TaskComplete] = {
    case Failure(e: Exception) => opFailure.applyOrElse(e, defaultFailure)
  }

  protected def opProgress: PartialFunction[OpProgress, TaskProgress]
  protected def opResult: PartialFunction[OpResult, TaskComplete]
  protected def opFailure: PartialFunction[Exception, TaskComplete]

  private def defaultProgress(p: OpProgress): TaskProgress = p match {
    case queued: Queued => TaskProgress(queued.percent, "queued")
    case started: Started => TaskProgress(started.percent, "started")
    case joined: Joined => TaskProgress(joined.percent, "joined")
    case x => TaskProgress(x.percent)
  }

  private def defaultResult(r: OpResult): TaskComplete = r match {
    case _ => TaskComplete()
  }

  private def defaultFailure(f: Exception): TaskComplete = f match {
    case e => TaskComplete.failed(e.getMessage)
  }
}

trait ExpectOpResult extends ExpectOp {
  protected def opProgress: PartialFunction[Any, TaskProgress] = PartialFunction.empty
}

trait ExpectOpFailure extends ExpectOpResult {
  protected def opResult: PartialFunction[OpResult, TaskComplete] = PartialFunction.empty
}


case class ExpectOpDefault() extends ExpectOpResult {
  protected def opFailure: PartialFunction[Exception, TaskComplete] = PartialFunction.empty
  protected def opResult: PartialFunction[OpResult, TaskComplete] = PartialFunction.empty
}


case class ExpectConversation() extends ExpectOpResult {
  def opFailure = {
    case fail: ConversationFailed => TaskComplete.failed(s"Failed ${ fail.failedStep }")
  }

  def opResult = {
    case _: ConversationSucceeded => TaskComplete()
  }
}
