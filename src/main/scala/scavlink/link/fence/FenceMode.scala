package scavlink.link.fence

object FenceMode extends Enumeration {
  val StayOut, StayIn, Report = Value
}

object FenceBreachAction extends Enumeration {
  /**
   * Report only, do nothing. Missions will continue to proceed.
   * This is probably the wrong choice. Use "None" only if you are handling fence breaches in a custom way.
   */
  val None = Value
  /**
   * Switch to Loiter mode.
   */
  val Loiter = Value
  /**
   * Switch to Land mode.
   */
  val Land = Value
  /**
   * Switch to ReturnToLaunch mode.
   */
  val ReturnToLaunch = Value
  /**
   * Use Guided mode to return to last known safe (non-breached) location.
   */
  val LastSafeLocation = Value
}
