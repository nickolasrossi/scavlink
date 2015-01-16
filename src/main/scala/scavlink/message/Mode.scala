package scavlink.message

import scavlink.message.enums.MavType
import scavlink.message.enums.MavType._

sealed class Mode(val name: String)(val func: PartialFunction[MavType.Value, Int]) {
  def apply(mtype: MavType.Value): Int = func(mtype)
  def isDefinedAt(mtype: MavType.Value): Boolean = func.isDefinedAt(mtype)
  override def toString = name
}

/**
 * Enumeration of modes that are common across vehicle types but have different mode ids.
 * Returns a vehicle-specific mode id which can be used with a setMode command.
 */
object Mode {
  type ModeFunc = PartialFunction[MavType.Value, Int]
  private var instances: Map[String, Mode] = Map.empty

  private def newMode(name: String)(func: ModeFunc): Mode = {
    val mode = new Mode(name)(func)
    instances += name -> mode
    mode
  }

  val Unknown: Mode = newMode("Unknown") {
    case _ => 0
  }

  val Guided: Mode = newMode("Guided") {
    case GROUND_ROVER | SURFACE_BOAT => RoverMode.Guided.id
    case FIXED_WING => FixedWingMode.Guided.id
    case QUADROTOR | OCTOROTOR | HEXAROTOR | TRICOPTER | HELICOPTER | COAXIAL => RotorMode.Guided.id
  }

  val Auto: Mode = newMode("Auto") {
    case GROUND_ROVER | SURFACE_BOAT => RoverMode.Auto.id
    case FIXED_WING => FixedWingMode.Auto.id
    case QUADROTOR | OCTOROTOR | HEXAROTOR | TRICOPTER | HELICOPTER | COAXIAL => RotorMode.Auto.id
  }

  val Loiter: Mode = newMode("Loiter") {
    case GROUND_ROVER | SURFACE_BOAT => RoverMode.Hold.id
    case FIXED_WING => FixedWingMode.Loiter.id
    case QUADROTOR | OCTOROTOR | HEXAROTOR | TRICOPTER | HELICOPTER | COAXIAL => RotorMode.Loiter.id
  }

  val ReturnToLaunch: Mode = newMode("ReturnToLaunch") {
    case GROUND_ROVER | SURFACE_BOAT => RoverMode.ReturnToLaunch.id
    case FIXED_WING => FixedWingMode.ReturnToLaunch.id
    case QUADROTOR | OCTOROTOR | HEXAROTOR | TRICOPTER | HELICOPTER | COAXIAL => RotorMode.ReturnToLaunch.id
  }

  val Land: Mode = newMode("Land") {
    case GROUND_ROVER | SURFACE_BOAT => RoverMode.Hold.id
    case QUADROTOR | OCTOROTOR | HEXAROTOR | TRICOPTER | HELICOPTER | COAXIAL => RotorMode.Land.id
  }

  val Manual: Mode = newMode("Manual") {
    case GROUND_ROVER | SURFACE_BOAT => RoverMode.Manual.id
    case FIXED_WING => FixedWingMode.Manual.id
    case QUADROTOR | OCTOROTOR | HEXAROTOR | TRICOPTER | HELICOPTER | COAXIAL => RotorMode.Stabilize.id
  }

  val Stabilize: Mode = newMode("Stabilize") {
    case FIXED_WING => FixedWingMode.Stabilize.id
    case QUADROTOR | OCTOROTOR | HEXAROTOR | TRICOPTER | HELICOPTER | COAXIAL => RotorMode.Stabilize.id
  }

  val Circle: Mode = newMode("Circle") {
    case FIXED_WING => FixedWingMode.Circle.id
    case QUADROTOR | OCTOROTOR | HEXAROTOR | TRICOPTER | HELICOPTER | COAXIAL => RotorMode.Circle.id
  }

  def apply(name: String): Mode = instances(name)

  def get(name: String): Option[Mode] = instances.get(name)

  /**
   * Look up the mode and name from the vehicle type and value.
   */
  def from(vehicleType: MavType.Value, value: Int): Option[Mode] = {
    val instance = instances.find { case (name, mode) =>
      mode.func.lift(vehicleType) == Some(value)
    }
    instance.map(_._2)
  }

  def values = instances
}


object RotorMode extends Enumeration {
  val Stabilize = Value(0)
  val Acro = Value(1)
  val AltitudeHold = Value(2)
  val Auto = Value(3)
  val Guided = Value(4)
  val Loiter = Value(5)
  val ReturnToLaunch = Value(6)
  val Circle = Value(7)
  val Land = Value(9)
  val Drift = Value(11)
  val Sport = Value(13)
  val PositionHold = Value(16)
}


object FixedWingMode extends Enumeration {
  val Manual = Value(0)
  val Circle = Value(1)
  val Stabilize = Value(2)
  val Training = Value(3)
  val Acro = Value(4)
  val FlyByWireA = Value(5)
  val FlyByWireB = Value(6)
  val Cruise = Value(7)
  val AutoTune = Value(8)
  val Auto = Value(10)
  val ReturnToLaunch = Value(11)
  val Loiter = Value(12)
  val Guided = Value(15)
}


object RoverMode extends Enumeration {
  val Manual = Value(0)
  val Learning = Value(2)
  val Steering = Value(3)
  val Hold = Value(4)
  val Auto = Value(10)
  val ReturnToLaunch = Value(11)
  val Guided = Value(15)
}
