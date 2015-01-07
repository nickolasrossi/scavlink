package scavlink.link.nav

/**
 * Error conditions for a failed course.
 */
object CourseError extends Enumeration {
  val OffCourse = Value
  val UnexpectedModeChange = Value
  val SetupFailed = Value
  val TeardownFailed = Value
  val ModeChangeFailed = Value
  val TelemetryTimeout = Value
}
