// Code generated by sbt-mavgen. Manual edits will be overwritten
package scavlink.message.enums

/**
 * These encode the sensors whose status is sent as part of the SYS_STATUS message.
 */
object MavSysStatusSensor extends Enumeration with Flag {
  val _UNKNOWN = Value(0)
  /**
   * 0x01 3D gyro
   */
  val _3D_GYRO = Value(1)
  /**
   * 0x02 3D accelerometer
   */
  val _3D_ACCEL = Value(2)
  /**
   * 0x04 3D magnetometer
   */
  val _3D_MAG = Value(4)
  /**
   * 0x08 absolute pressure
   */
  val ABSOLUTE_PRESSURE = Value(8)
  /**
   * 0x10 differential pressure
   */
  val DIFFERENTIAL_PRESSURE = Value(16)
  /**
   * 0x20 GPS
   */
  val GPS = Value(32)
  /**
   * 0x40 optical flow
   */
  val OPTICAL_FLOW = Value(64)
  /**
   * 0x80 computer vision position
   */
  val VISION_POSITION = Value(128)
  /**
   * 0x100 laser based position
   */
  val LASER_POSITION = Value(256)
  /**
   * 0x200 external ground truth (Vicon or Leica)
   */
  val EXTERNAL_GROUND_TRUTH = Value(512)
  /**
   * 0x400 3D angular rate control
   */
  val ANGULAR_RATE_CONTROL = Value(1024)
  /**
   * 0x800 attitude stabilization
   */
  val ATTITUDE_STABILIZATION = Value(2048)
  /**
   * 0x1000 yaw position
   */
  val YAW_POSITION = Value(4096)
  /**
   * 0x2000 z/altitude control
   */
  val Z_ALTITUDE_CONTROL = Value(8192)
  /**
   * 0x4000 x/y position control
   */
  val XY_POSITION_CONTROL = Value(16384)
  /**
   * 0x8000 motor outputs / control
   */
  val MOTOR_OUTPUTS = Value(32768)
  /**
   * 0x10000 rc receiver
   */
  val RC_RECEIVER = Value(65536)
  /**
   * 0x20000 2nd 3D gyro
   */
  val _3D_GYRO2 = Value(131072)
  /**
   * 0x40000 2nd 3D accelerometer
   */
  val _3D_ACCEL2 = Value(262144)
  /**
   * 0x80000 2nd 3D magnetometer
   */
  val _3D_MAG2 = Value(524288)
  /**
   * 0x100000 geofence
   */
  val GEOFENCE = Value(1048576)
  /**
   * 0x200000 AHRS subsystem health
   */
  val AHRS = Value(2097152)
  /**
   * 0x400000 Terrain subsystem health
   */
  val TERRAIN = Value(4194304)
}