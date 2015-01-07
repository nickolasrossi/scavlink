package scavlink.link.operation

import akka.actor.Actor

import scala.concurrent.duration._

sealed trait TimerOp extends Op {
  def delay: FiniteDuration
  val actorType: Class[_ <: Actor] = classOf[TimerActor]
}

case class Timer(id: String, delay: FiniteDuration) extends TimerOp
case class FailingTimer(id: String, delay: FiniteDuration) extends TimerOp
case class ActorStoppingTimer(id: String, delay: FiniteDuration) extends TimerOp
case class TimerWithProgress(id: String, count: Int, delay: FiniteDuration) extends TimerOp

case class TimerProgress(op: TimerWithProgress, number: Int, total: Int) extends OpProgress {
  val percent: Int = ((number.toFloat / total) * 100).toInt
}
case class TimerFinished(op: TimerOp) extends OpResult
case class TimerFailed(op: FailingTimer) extends OpException

case class SubTimer(id: String, delay: FiniteDuration, depth: Int) extends TimerOp {
  override val actorType: Class[_ <: Actor] = classOf[SubTimerActor]
}
case class SubTimerFinished(op: SubTimer) extends OpResult

class NonTimerOp extends Op {
  val actorType: Class[_ <: Actor] = classOf[TimerActor]
}


class TimerOpSupervisor extends OpSupervisor[TimerOp, Option[Any]] {
  val opClass = classOf[TimerOp]
  val opProps = None
}


class TimerActor(props: Any) extends Actor {
  import context.dispatcher

  def receive: Receive = {
    case op@Timer(_, delay) =>
      context.system.scheduler.scheduleOnce(delay, sender(), TimerFinished(op))

    case op@FailingTimer(_, delay) =>
      context.system.scheduler.scheduleOnce(delay, sender(), TimerFailed(op))

    case op@ActorStoppingTimer(_, delay) =>
      context.system.scheduler.scheduleOnce(delay) {
        context.stop(self)
      }

    case op@TimerWithProgress(_, count, delay) =>
      (1 until count) foreach { i =>
        context.system.scheduler.scheduleOnce(delay * i, sender(), TimerProgress(op, i, count))
      }
      context.system.scheduler.scheduleOnce(delay * count, sender(), TimerFinished(op))
  }
}

class SubTimerActor(props: Any) extends Actor {
  import context.dispatcher
  private var _op: Option[SubTimer] = None

  def receive: Receive = {
    case op@SubTimer(id, delay, depth) =>
      _op = Some(op)
      val newOp = if (depth > 0) SubTimer(id, delay, depth - 1) else Timer(id, delay)
      context.system.scheduler.scheduleOnce(delay, sender(), newOp)

    case SubTimerFinished(op: SubTimer) =>
      _op foreach { o =>
        sender() ! SubTimerFinished(o)
      }

    case TimerFinished(op: Timer) =>
      _op foreach { o =>
        sender() ! SubTimerFinished(o)
      }
  }
}
