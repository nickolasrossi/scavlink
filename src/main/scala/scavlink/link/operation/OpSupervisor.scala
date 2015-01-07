package scavlink.link.operation

import akka.actor.Status.Failure
import akka.actor._

import scala.collection.mutable

/**
 * Starts and supervises long-lived operation actors, brokering messages to the original submitter,
 * allowing only one primary operation at a time, queuing others in order of submission.
 * Sub-operations submitted by the primary operation actor are executed immediately, however,
 * as are sub-sub-operations and so on, so that an operation may use sub-operations to accomplish its task.
 *
 * The main use case for OpSupervisor is to execute tasks against a vehicle that involve
 * many stages and packet conversations, and to prevent competing tasks from executing concurrently.
 * For instance, the vehicle navigation supervisor only allows one nav operation at a time;
 * it would cause havoc to have two actors trying to fly a vehicle to two different locations.
 *
 * However, separate OpSupervisors may be started for different vehicle functions that can
 * safely operate independently. For instance, OpSupervisors in charge of parameter loading, a camera,
 * or other components may execute operations in parallel with the navigation supervisor.
 *
 * OpSupervisor isn't just tied to vehicle operations; it's abstract enough to to be used for any
 * category of operations.
 *
 * A misbehaving application can easily circumvent an OpSupervisor by running an operation actor
 * directly or maliciously submitting emergency operations. However, if used properly, it keeps
 * bad things from happening due to colliding operations.
 *
 * Other behaviors:
 *
 * When an operation finishes, the original actor that submitted the operation receives a
 * result message (success) or exception (failure).
 *
 * If a submitted operation is identical to one already queued or in progress, the context of the
 * existing operation is reused for the new one.
 *
 * An operation queued or in progress can be cancelled by sending the operation wrapped
 * in a [[Cancel]] object.
 *
 * Flags may be submitted in a tuple with the operation to modify behaviors:
 *
 * [[WithProgress]] will enable forwarding of progress messages sent by the operation actor
 * back to the origin actor. This flag can't be used with the Akka ask (?) pattern, because
 * the Future over the result would complete as soon as it receives the first progress message.
 *
 * [[WithContext]] will prepend the specified context value in a tuple with any message
 * sent back to the origin (i.e. origin ! (ctx, msg)).
 * The origin actor determines the type and value of this context.
 *
 * [[Emergency]] causes all other running and queued operations to be interrupted and terminated
 * so the new operation may start immediately. It's meant for failsafe conditions, such as a
 * low battery warning or collision alert.
 *
 * @tparam O base type of allowed <i>primary</i> operations (sub-operations may be any type)
 * @tparam P type of single props argument passed to all operation actors
 *
 * @author Nick Rossi
 */
trait OpSupervisor[O <: Op, P] extends Actor with ActorLogging {
  def opClass: Class[O]
  def opProps: P

  private var primary: Option[Op] = None
  private var running: Map[Op, (ActorRef, Responder)] = Map.empty
  private val queue: mutable.LinkedHashMap[Op, Responder] = mutable.LinkedHashMap.empty

  def opsPending: Boolean = running.nonEmpty


  private case class Origin(actor: ActorRef, ctx: Option[Any] = None) {
    def send(msg: Any): Unit = actor ! (ctx match {
      case Some(x) => (x, msg)
      case None => msg
    })
  }

  private case class Responder(result: mutable.Set[Origin], progress: mutable.Set[Origin]) {
    def sendResult(msg: Any): Unit = result.foreach(_.send(msg))
    def sendProgress(msg: Any): Unit = progress.foreach(_.send(msg))
    def find(actor: ActorRef): Option[Origin] = result.find(_.actor == actor)
    def contains(origin: Origin): Boolean = result.contains(origin)
    def remove(origin: Origin) = copy(result = result - origin, progress = progress - origin)
  }


  /**
   * Submit a new operation.
   * If identical to a running operation, the existing operation will be joined.
   * If identical to a queued operation, that operation will be joined.
   * If there is no primary operation running, the new operation is started immediately.
   * Otherwise, the new operation is placed in the queue.
   * @param flags whether to send progress messages to the origin actor
   */
  def submit(op: Op, flags: OpFlags = OpFlags()): Unit = {
    var origin = Origin(sender())
    var withProgress = false

    flags.flags foreach {
      case Emergency => emergencyPreempt(op)
      case WithContext(ctx) => origin = origin.copy(ctx = Some(ctx))
      case WithProgress => withProgress = true
    }

    def addTo(responder: Responder): Unit = {
      if (origin.actor != self) {
        context.watch(origin.actor)
        responder.result += origin
        if (withProgress) {
          responder.progress += origin
        }
      }
    }

    running.get(op) match {
      case Some((_, responder)) =>
        log.debug(s"Reuse running $op")
        addTo(responder)
        if (withProgress) {
          origin.send(Joined(op))
        }

      case None =>
        queue.get(op) match {
          case Some(responder) =>
            log.debug(s"Reuse queued $op")
            addTo(responder)
            if (withProgress) {
              origin.send(Joined(op))
            }

          case None =>
            val responder = Responder(mutable.Set.empty, mutable.Set.empty)
            addTo(responder)

            // operation is allowed if it's a new primary, or if sent by the actor of an existing operation
            val hasParent = origin.actor == self ||
                            running.exists { case (_, (actor, _)) => actor == origin.actor }

            if (!hasParent && !opClass.isAssignableFrom(op.getClass)) {
              responder.sendResult(Failure(new IllegalOperationException(op)))
            } else if (primary.isEmpty || hasParent) {
              start(op, responder)
            } else {
              queue(op, responder)
            }
        }
    }
  }

  /**
   * Kills any running and queued operations except for the specified operation,
   * which will become the emergency operation if it doesn't already exist.
   *
   * If a primary operation was running, this will stop the primary actor, kill the whole queue.
   * then place the emergency operation on the empty queue - so when the Terminated message
   * arrives for the stopped primary actor, the emergency operation is the next queue item to be started.
   * This prevents any residual actions in the primary actor from interfering with the emergency operation.
   */
  def emergencyPreempt(emergencyOp: Op): Unit = {
    log.debug(s"EMERGENCY: $emergencyOp")

    primary foreach { o =>
      if (o != emergencyOp) {
        log.debug(s"Stopping $o")
        context.stop(running(o)._1)
      }

      val reusePromise = queue.remove(emergencyOp)
      queue foreach { case (qop, responder) =>
        log.debug(s"Canceling $qop")
        responder.sendResult(Failure(new UnexpectedTerminationException(qop)))
      }

      queue.clear()
      reusePromise foreach { pr =>
        queue += emergencyOp -> pr
      }
    }
  }

  /**
   * Starts the actor for a new operation and sends it the operation as a message.
   * Marks it as the primary if there isn't one already.
   */
  private def start(op: Op, responder: Responder): Unit = {
    log.debug(s"Starting $op")
    val actor = context.actorOf(Props(op.actorType, opProps), op.name + "_" + System.currentTimeMillis())
    context.watch(actor)

    if (primary == None) primary = Some(op)
    running += op ->(actor, responder)
    actor ! op

    responder.sendProgress(Started(op))
  }

  private def queue(op: Op, responder: Responder): Unit = {
    log.debug(s"Queuing $op")
    queue += op -> responder
    responder.sendProgress(Queued(op))
  }

  /**
   * Updates progress listeners with the progress message.
   */
  def update(progress: OpProgress): Unit = {
    running.get(progress.op) foreach { case (actor, responder) =>
      log.debug(s"Updating $progress")
      responder.sendProgress(progress)
    }
  }

  /**
   * Finishes an operation for which a result message was received.
   * If the finished operation was the primary, the next queued operation is started (if any).
   */
  def finish(result: OpResult): Unit = {
    running.get(result.op) foreach { case (actor, responder) =>
      running -= result.op
      if (primary == Some(result.op)) primary = None

      result match {
        case e: OpException =>
          log.debug(s"Failing ${ e.op }")
          val fail = Failure(e)
          responder.sendResult(fail)

        case r =>
          log.debug(s"Finishing ${ r.op }")
          responder.sendResult(r)
      }
    }

    if (primary == None) {
      if (queue.nonEmpty) {
        val (op, pr) = queue.iterator.next()
        log.debug(s"Dequeuing $op")
        queue.remove(op)
        start(op, pr)
      }
    }
  }

  /**
   * Cancels a running or queued operation submitted by the origin sender.
   * If other origins also requested this operation, the operation is not actually terminated.
   * No message is returned if there is nothing to cancel.
   */
  def cancel(op: Op): Unit = {
    log.debug(s"Canceling $op from ${sender()}")

    for ((actor, responder) <- running.get(op); origin <- responder.find(sender())) {
      cancelRunning(op, actor, responder, origin)
    }

    for (responder <- queue.get(op); origin <- responder.find(sender())) {
      cancelQueued(op, responder, origin)
    }
  }

  /**
   * Cancels a running or queued operation by context, submitted by the origin sender.
   * If other origins also requested this operation, the operation is not actually terminated.
   * No message is returned if there is nothing to cancel.
   */
  def cancelContext(ctx: Any): Unit = {
    log.debug(s"Cancelling by context $ctx from ${sender()}")
    val origin = Origin(sender(), Some(ctx))

    for ((op, (actor, responder)) <- running if responder.contains(origin)) {
      cancelRunning(op, actor, responder, origin)
    }

    for ((op, responder) <- queue if responder.contains(origin)) {
      cancelQueued(op, responder, origin)
    }
  }

  private def cancelRunning(op: Op, actor: ActorRef, responder: Responder, origin: Origin, send: Boolean = true): Unit = {
    val newResponder = responder.remove(origin)
    running += op ->(actor, newResponder)
    if (send) origin.send(Failure(new CancelledException(op)))

    if (newResponder.result.isEmpty) {
      log.debug(s"Stopping orphaned op: $op")
      context.stop(actor)
    }
  }

  private def cancelQueued(op: Op, responder: Responder, origin: Origin, send: Boolean = true): Unit = {
    val newResponder = responder.remove(origin)
    queue += op -> newResponder
    if (send) origin.send(Failure(new CancelledException(op)))

    if (newResponder.result.isEmpty) {
      log.debug(s"Removing orphaned op from queue: $op")
      queue.remove(op)
    }
  }

  /**
   * A terminated actor could be either an operation or an origin actor.
   * We can't know for sure, so we search running and queued operations for both cases.
   */
  private def reap(terminated: ActorRef): Unit = {
    // if an operation, report a termination exception back to origin
    running.find { case (_, (actor, _)) => actor == terminated } match {
      case Some((op, _)) =>
        log.debug(s"Unexpected termination of $op")
        finish(new UnexpectedTerminationException(op))

      case None => //
    }

    // if an origin actor, remove it from responder lists
    // any operation left with an empty responder list will be stopped.
    for {
      (op, (actor, responder)) <- running
      origin <- responder.result.filter(_.actor == terminated)
    } yield {
      log.debug(s"Canceling $op to $origin")
      cancelRunning(op, actor, responder, origin, false)
    }

    // if an origin actor, remove it from queue
    // any operation left with an empty responder list will be removed.
    for {
      (op, responder) <- queue
      origin <- responder.result.filter(_.actor == terminated)
    } yield {
      log.debug(s"Canceling queued $op to $origin")
      cancelQueued(op, responder, origin, false)
    }
  }


  def receive: Receive = opHandler orElse failureHandler

  def opHandler: Receive = {
    case op: Op => submit(op)
    case (flag: OpFlag, op: Op) => submit(op, OpFlags(flag))
    case (flags: OpFlags, op: Op) => submit(op, flags)
    case Cancel(op: Op) => cancel(op)
    case CancelContext(ctx) => cancelContext(ctx)

    case progress: OpProgress => update(progress)
    case result: OpResult => finish(result)
  }

  def failureHandler: Receive = {
    case Failure(error: OpException) => finish(error)
    case Terminated(actor) => reap(actor)
  }
}

class CancelledException(val op: Op) extends Exception
class UnexpectedTerminationException(val op: Op) extends OpException
class IllegalOperationException(val op: AnyRef) extends Exception
