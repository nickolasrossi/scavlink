package scavlink.task

import akka.actor.Status.Failure
import scavlink.link.operation._

import scala.annotation.StaticAnnotation

/**
 * Partial functions that convert received actor messages into
 * TaskProgress or TaskResult objects for response to a web invoke.
 * @author Nick Rossi
 */
trait Expect extends StaticAnnotation {
  def progress: PartialFunction[Any, TaskProgress]
  def result: PartialFunction[Any, TaskComplete]
  def failure: PartialFunction[Any, TaskComplete]
}

/**
 * Implementation for messages received from [[OpSupervisor]].
 */
trait ExpectOp extends Expect {
  final def progress: PartialFunction[Any, TaskProgress] = {
    case (id: TaskId, p: OpProgress) => opProgress.applyOrElse((id, p), defaultProgress)
  }

  final def result: PartialFunction[Any, TaskComplete] = {
    case (id: TaskId, r: OpResult) => opResult.applyOrElse((id, r), defaultResult)
  }

  final def failure: PartialFunction[Any, TaskComplete] = {
    case (id: TaskId, Failure(e: OpException)) => opFailure.applyOrElse((id, e), defaultFailure)
  }

  protected def opProgress: PartialFunction[(TaskId, OpProgress), TaskProgress]
  protected def opResult: PartialFunction[(TaskId, OpResult), TaskComplete]
  protected def opFailure: PartialFunction[(TaskId, OpException), TaskComplete]

  private def defaultProgress(p: (TaskId, OpProgress)): TaskProgress = p match {
    case (id, queued: Queued) => TaskProgress(id, queued.percent, "queued")
    case (id, started: Started) => TaskProgress(id, started.percent, "started")
    case (id, joined: Joined) => TaskProgress(id, joined.percent, "joined")
    case (id, x) => TaskProgress(id, x.percent)
  }

  private def defaultResult(r: (TaskId, OpResult)): TaskComplete = r match {
    case (id, _) => TaskComplete(id)
  }

  private def defaultFailure(f: (TaskId, OpException)): TaskComplete = f match {
    case (id, e) => TaskComplete.failed(id, e.getMessage)
  }
}

trait ExpectOpResult extends ExpectOp {
  protected def opProgress: PartialFunction[Any, TaskProgress] = PartialFunction.empty
}


case class ExpectOpDefault() extends ExpectOpResult {
  protected def opFailure: PartialFunction[(TaskId, OpException), TaskComplete] = PartialFunction.empty
  protected def opResult: PartialFunction[(TaskId, OpResult), TaskComplete] = PartialFunction.empty
}


case class ExpectConversation() extends ExpectOpResult {
  def opFailure = {
    case (id: TaskId, fail: ConversationFailed) => TaskComplete.failed(id, s"Failed ${ fail.failedStep }")
  }

  def opResult = {
    case (id: TaskId, _: ConversationSucceeded) => TaskComplete(id)
  }
}
