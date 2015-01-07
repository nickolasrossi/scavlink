package scavlink.link.operation

import akka.actor.Status.Failure
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{TestKit, TestProbe}
import akka.util.Timeout
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._
import scala.util.Random

class OpSupervisorSpec(_system: ActorSystem)
  extends TestKit(_system) with WordSpecLike with Matchers with BeforeAndAfterAll {
  val random = new Random(System.currentTimeMillis())
  implicit val timeout = Timeout(1.minute)

  def this() = this(ActorSystem("OpSupervisorSpec"))

  def withSupervisor(testCode: ActorRef => Any): Unit = {
    val actor = system.actorOf(Props(classOf[TimerOpSupervisor]), "TimerOpSupervisor_" + random.nextInt)
    try {
      testCode(actor)
    } finally {
      system.stop(actor)
    }
  }

  def expectTheUnexpected(op: Op): PartialFunction[Any, Boolean] = {
    case Failure(e: UnexpectedTerminationException) => e.op == op
  }

  def expectCancelled(op: Op): PartialFunction[Any, Boolean] = {
    case Failure(e: CancelledException) => e.op == op
  }

  def expectCancelledByContext(ctx: Any, op: Op): PartialFunction[Any, Boolean] = {
    case (`ctx`, Failure(e: CancelledException)) => e.op == op
  }


  "an OpSupervisor" should {
    "report a completed operation" in withSupervisor { supervisor =>
      val probe = new TestProbe(system)
      val op = Timer("A", 1.second)

      supervisor.tell(op, probe.ref)
      probe.expectMsg(2.seconds, TimerFinished(op))
    }

    "report a completed operation with a context value" in withSupervisor { supervisor =>
      val probe = new TestProbe(system)
      val op = Timer("A", 1.second)

      supervisor.tell(WithContext(12345)(op), probe.ref)
      probe.expectMsg(2.seconds, (12345, TimerFinished(op)))
    }

    "report a failed operation" in withSupervisor { supervisor =>
      val probe = new TestProbe(system)
      val op = FailingTimer("B", 1.second)

      supervisor.tell(op, probe.ref)
      probe.expectMsg(2.seconds, Failure(TimerFailed(op)))
    }

    "report a failed operation with a context value" in withSupervisor { supervisor =>
      val probe = new TestProbe(system)
      val op = FailingTimer("B", 1.second)

      supervisor.tell(WithContext(12345)(op), probe.ref)
      probe.expectMsg(2.seconds, (12345, Failure(TimerFailed(op))))
    }

    "handle actor termination" in withSupervisor { supervisor =>
      val probe = new TestProbe(system)
      val op = ActorStoppingTimer("S", 1.second)

      supervisor.tell(op, probe.ref)
      probe.expectMsgPF(3.seconds)(expectTheUnexpected(op))
    }

    "disallow operations of the wrong type" in withSupervisor { supervisor =>
      val probe = new TestProbe(system)
      val op = new NonTimerOp

      supervisor.tell(op, probe.ref)

      probe.expectMsgPF(1.second) {
        case Failure(e: IllegalOperationException) => e.op == op
      }
    }

    "reuse a running operation" in withSupervisor { supervisor =>
      val probe1 = new TestProbe(system)
      val probe2 = new TestProbe(system)
      val op = Timer("A", 3.seconds)

      supervisor.tell(op, probe1.ref)
      Thread.sleep(500)
      supervisor.tell(op, probe2.ref)

      probe1.expectMsg(4.seconds, TimerFinished(op))
      probe2.expectMsg(1.seconds, TimerFinished(op))
    }

    "allow descendant operations without queuing" in withSupervisor { supervisor =>
      val probe = new TestProbe(system)
      val op = SubTimer("A", 1.second, 1)

      supervisor.tell(op, probe.ref)
      probe.expectMsg(6.seconds, SubTimerFinished(op))
    }

    "forward progress messages when requested" in withSupervisor { supervisor =>
      val probe = new TestProbe(system)
      val op = TimerWithProgress("A", 4, 500.milliseconds)

      supervisor.tell(WithProgress(op), probe.ref)

      probe.expectMsg(1.second, Started(op))
      probe.expectMsg(1.second, TimerProgress(op, 1, 4))
      probe.expectMsg(2.seconds, TimerProgress(op, 2, 4))
      probe.expectMsg(2.seconds, TimerProgress(op, 3, 4))
      probe.expectMsg(3.seconds, TimerFinished(op))
    }

    "not forward progress messages when not requested" in withSupervisor { supervisor =>
      val probe = new TestProbe(system)
      val op = TimerWithProgress("A", 3, 1.second)

      supervisor.tell(op, probe.ref)

      probe.expectMsg(4.seconds, TimerFinished(op))
    }

    "include a context value with the result when requested" in withSupervisor { supervisor =>
      val probe = new TestProbe(system)
      val op = TimerWithProgress("A", 4, 500.milliseconds)

      val flags = OpFlags(WithContext(12345), WithProgress)
      supervisor.tell((flags, op), probe.ref)

      probe.expectMsg(1.second, (12345, Started(op)))
      probe.expectMsg(1.second, (12345, TimerProgress(op, 1, 4)))
      probe.expectMsg(2.seconds, (12345, TimerProgress(op, 2, 4)))
      probe.expectMsg(2.seconds, (12345, TimerProgress(op, 3, 4)))
      probe.expectMsg(3.seconds, (12345, TimerFinished(op)))
    }

    "cancel an operation if its origin actor terminates" in pending
  }

  "the queue" should {
    "complete a second operation after the first has completed" in withSupervisor { supervisor =>
      val probe1 = new TestProbe(system)
      val probe2 = new TestProbe(system)

      val op1 = Timer("A", 2.seconds)
      val op2 = Timer("B", 1.seconds)

      supervisor.tell(op1, probe1.ref)
      supervisor.tell(op2, probe2.ref)

      probe1.expectMsg(3.seconds, TimerFinished(op1))
      probe2.expectMsg(3.seconds, TimerFinished(op2))
    }

    "complete several queued operations" in withSupervisor { supervisor =>
      val probe1 = new TestProbe(system)
      val probe2 = new TestProbe(system)
      val probe3 = new TestProbe(system)
      val probe4 = new TestProbe(system)

      val op1 = Timer("A", 2.second)
      val op2 = Timer("B", 1.second)
      val op3 = Timer("C", 1.second)
      val op4 = Timer("D", 1.second)

      supervisor.tell(op1, probe1.ref)
      supervisor.tell(op2, probe2.ref)
      supervisor.tell(op3, probe3.ref)
      supervisor.tell(op4, probe4.ref)

      probe1.expectMsg(3.seconds, TimerFinished(op1))
      probe2.expectMsg(2.seconds, TimerFinished(op2))
      probe3.expectMsg(2.seconds, TimerFinished(op3))
      probe4.expectMsg(2.seconds, TimerFinished(op4))
    }

    "reuse a queued operation" in withSupervisor { supervisor =>
      val probe1 = new TestProbe(system)
      val probe2 = new TestProbe(system)
      val probe3 = new TestProbe(system)

      val op1 = Timer("A", 2.seconds)
      val op2 = Timer("B", 2.seconds)

      supervisor.tell(op1, probe1.ref)
      supervisor.tell(op2, probe2.ref)
      supervisor.tell(op2, probe3.ref)

      probe1.expectMsg(3.seconds, TimerFinished(op1))
      probe2.expectMsg(3.seconds, TimerFinished(op2))
      probe3.expectMsg(200.milliseconds, TimerFinished(op2))
    }

    "handle actor termination and continue queue" in withSupervisor { supervisor =>
      val probe1 = new TestProbe(system)
      val probe2 = new TestProbe(system)
      val probe3 = new TestProbe(system)

      val op1 = ActorStoppingTimer("A", 2.seconds)
      val op2 = Timer("B", 1.second)
      val op3 = Timer("C", 1.second)

      supervisor.tell(op1, probe1.ref)
      supervisor.tell(op2, probe2.ref)
      supervisor.tell(op3, probe3.ref)

      probe1.expectMsgPF(3.seconds)(expectTheUnexpected(op1))
      probe2.expectMsg(3.seconds, TimerFinished(op2))
      probe3.expectMsg(3.seconds, TimerFinished(op3))
    }
  }

  "cancellation" should {
    "cancel the primary operation" in withSupervisor { supervisor =>
      val probe1 = new TestProbe(system)
      val probe2 = new TestProbe(system)
      val probe3 = new TestProbe(system)
      val op1 = Timer("A", 3.seconds)
      val op2 = Timer("B", 1.second)

      supervisor.tell(op1, probe1.ref)
      supervisor.tell(op2, probe2.ref)

      Thread.sleep(200)
      supervisor.tell(Cancel(op1), probe1.ref)

      probe1.expectMsgPF(1.second)(expectCancelled(op1))

      probe2.expectMsg(2.seconds, TimerFinished(op2))
    }

    "cancel a queued operation" in withSupervisor { supervisor =>
      val probe1 = new TestProbe(system)
      val probe2 = new TestProbe(system)
      val probe3 = new TestProbe(system)
      val probe4 = new TestProbe(system)
      val probe5 = new TestProbe(system)

      val op1 = Timer("A", 2.second)
      val op2 = Timer("B", 1.second)
      val op3 = Timer("C", 1.second)
      val op4 = Timer("D", 1.second)

      supervisor.tell(op1, probe1.ref)
      supervisor.tell(op2, probe2.ref)
      supervisor.tell(op3, probe3.ref)
      supervisor.tell(op4, probe4.ref)

      Thread.sleep(200)
      supervisor.tell(Cancel(op3), probe3.ref)

      probe3.expectMsgPF(1.seconds)(expectCancelled(op3))

      probe1.expectMsg(3.seconds, TimerFinished(op1))
      probe2.expectMsg(2.seconds, TimerFinished(op2))
      probe4.expectMsg(2.seconds, TimerFinished(op4))
    }

    "cancel by context value" in withSupervisor { supervisor =>
      val probe1 = new TestProbe(system)
      val probe2 = new TestProbe(system)
      val probe3 = new TestProbe(system)
      val op1 = Timer("A", 3.seconds)
      val op2 = Timer("B", 1.second)

      supervisor.tell(WithContext(12345)(op1), probe1.ref)
      supervisor.tell(WithContext(67890)(op2), probe2.ref)

      Thread.sleep(200)
      supervisor.tell(CancelContext(12345), probe1.ref)

      probe1.expectMsgPF(1.second)(expectCancelledByContext(12345, op1))

      probe2.expectMsg(2.seconds, (67890, TimerFinished(op2)))
    }

    "not cancel operation if not requested from the originating actor" in withSupervisor { supervisor =>
      val probe1 = new TestProbe(system)
      val probe2 = new TestProbe(system)
      val probe3 = new TestProbe(system)
      val op1 = Timer("A", 3.seconds)
      val op2 = Timer("B", 1.second)

      supervisor.tell(op1, probe1.ref)
      supervisor.tell(op2, probe2.ref)

      Thread.sleep(200)
      supervisor.tell(Cancel(op1), probe3.ref)

      probe1.expectMsg(4.seconds, TimerFinished(op1))
      probe2.expectMsg(2.seconds, TimerFinished(op2))
    }
  }

  "an Emergency operation" should {
    "interrupt running and queued operations" in withSupervisor { supervisor =>
      val probe1 = new TestProbe(system)
      val probe2 = new TestProbe(system)
      val probe3 = new TestProbe(system)
      val probe4 = new TestProbe(system)
      val op1 = Timer("A", 2.second)
      val op2 = Timer("B", 2.second)
      val op3 = Timer("C", 2.second)
      val eop = Timer("E", 2.second)

      supervisor.tell(op1, probe1.ref)
      supervisor.tell(op2, probe2.ref)
      supervisor.tell(op3, probe3.ref)
      Thread.sleep(100)
      supervisor.tell(Emergency(eop), probe4.ref)

      probe1.expectMsgPF(500.milliseconds)(expectTheUnexpected(op1))
      probe2.expectMsgPF(500.milliseconds)(expectTheUnexpected(op2))
      probe3.expectMsgPF(500.milliseconds)(expectTheUnexpected(op3))
      probe4.expectMsg(4.seconds, TimerFinished(eop))
    }

    "interrupt and execute with a context value" in withSupervisor { supervisor =>
      val probe1 = new TestProbe(system)
      val probe2 = new TestProbe(system)
      val op1 = Timer("A", 2.second)
      val eop = Timer("E", 2.second)

      supervisor.tell(op1, probe1.ref)
      Thread.sleep(100)
      val flags = OpFlags(Emergency, WithContext(12345))
      supervisor.tell((flags, eop), probe2.ref)

      probe1.expectMsgPF(500.milliseconds)(expectTheUnexpected(op1))
      probe2.expectMsg(4.seconds, (12345, TimerFinished(eop)))
    }

    "reuse an equivalent primary operation" in withSupervisor { supervisor =>
      val probe1 = new TestProbe(system)
      val probe2 = new TestProbe(system)
      val op = Timer("A", 2.second)

      supervisor.tell(op, probe1.ref)
      Thread.sleep(500)
      supervisor.tell(Emergency(op), probe2.ref)

      probe2.expectMsg(3.seconds, TimerFinished(op))
      probe1.expectMsg(200.milliseconds, TimerFinished(op))
    }

    "reuse an equivalent queued operation" in withSupervisor { supervisor =>
      val probe1 = new TestProbe(system)
      val probe2 = new TestProbe(system)
      val probe3 = new TestProbe(system)
      val op1 = Timer("A", 2.second)
      val op2 = Timer("B", 2.second)

      supervisor.tell(op1, probe1.ref)
      supervisor.tell(op2, probe2.ref)
      Thread.sleep(500)
      supervisor.tell(Emergency(op2), probe3.ref)

      probe1.expectMsgPF(500.milliseconds)(expectTheUnexpected(op1))
      probe2.expectMsg(3.seconds, TimerFinished(op2))
      probe3.expectMsg(200.milliseconds, TimerFinished(op2))
    }
  }
}
