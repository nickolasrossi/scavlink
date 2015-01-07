package scavlink.link.operation

/**
 * Modifier flags that can be included when submitting an operation.
 */
sealed trait OpFlag {
  def |(that: OpFlag) = OpFlags(Set(this, that))
  def |(that: OpFlags) = that | this
}

// wrap flags in a case class so OpSupervisor can match with strong typing
case class OpFlags(flags: Set[OpFlag]) {
  def |(flag: OpFlag): OpFlags = OpFlags(flags + flag)

  def |(flag: Option[OpFlag]): OpFlags = flag match {
    case Some(f) => this | f
    case None => this
  }
}

object OpFlags {
  def apply(flags: OpFlag*): OpFlags = OpFlags(Set(flags:_*))
}

/**
 * Attaches a context value to the operation, which is prepended to progress
 * and result messages in a tuple sent back to the origin actor.
 */
case class WithContext(ctx: Any) extends OpFlag {
  def apply(op: Op) = (this, op)
}

/**
 * Forward progress messages from the operation, if any, to the origin actor.
 * (Note that this can't be used with the ask pattern, because the private future
 * created by ask will terminate prematurely when it receives a progress message.)
 */
case object WithProgress extends OpFlag {
  def apply(op: Op) = (WithProgress, op)
}

/**
 * Starts an emergency operation that preempts any running or queued operations.
 */
case object Emergency extends OpFlag {
  def apply(op: Op) = (Emergency, op)
}
