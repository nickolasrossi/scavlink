// Code generated by sbt-mavgen. Manual edits will be overwritten
package scavlink.message.slugs

import scavlink.message._
import scavlink.message.enums._

/**
 * Sensor and DSC control loads.
 * @param sensLoad Sensor DSC Load
 * @param ctrlLoad Control DSC Load
 * @param batVolt Battery Voltage in millivolts
 */
case class CpuLoad(sensLoad: Byte = 0, ctrlLoad: Byte = 0, batVolt: Short = 0)
extends Message {
  val _id = 170
  val _name = "CPU_LOAD"
  val _bundle = Bundle.slugs
  override def toString = "CPU_LOAD" + " sensLoad=" + sensLoad + " ctrlLoad=" + ctrlLoad + " batVolt=" + batVolt
}

/**
 * Accelerometer and gyro biases.
 * @param axBias Accelerometer X bias (m/s)
 * @param ayBias Accelerometer Y bias (m/s)
 * @param azBias Accelerometer Z bias (m/s)
 * @param gxBias Gyro X bias (rad/s)
 * @param gyBias Gyro Y bias (rad/s)
 * @param gzBias Gyro Z bias (rad/s)
 */
case class SensorBias(axBias: Float = 0, ayBias: Float = 0, azBias: Float = 0, gxBias: Float = 0, gyBias: Float = 0, gzBias: Float = 0)
extends Message {
  val _id = 172
  val _name = "SENSOR_BIAS"
  val _bundle = Bundle.slugs
  override def toString = "SENSOR_BIAS" + " axBias=" + axBias + " ayBias=" + ayBias + " azBias=" + azBias + " gxBias=" + gxBias + " gyBias=" + gyBias + " gzBias=" + gzBias
}

/**
 * Configurable diagnostic messages.
 * @param diagFl1 Diagnostic float 1
 * @param diagFl2 Diagnostic float 2
 * @param diagFl3 Diagnostic float 3
 * @param diagSh1 Diagnostic short 1
 * @param diagSh2 Diagnostic short 2
 * @param diagSh3 Diagnostic short 3
 */
case class Diagnostic(diagFl1: Float = 0, diagFl2: Float = 0, diagFl3: Float = 0, diagSh1: Short = 0, diagSh2: Short = 0, diagSh3: Short = 0)
extends Message {
  val _id = 173
  val _name = "DIAGNOSTIC"
  val _bundle = Bundle.slugs
  override def toString = "DIAGNOSTIC" + " diagFl1=" + diagFl1 + " diagFl2=" + diagFl2 + " diagFl3=" + diagFl3 + " diagSh1=" + diagSh1 + " diagSh2=" + diagSh2 + " diagSh3=" + diagSh3
}

/**
 * Data used in the navigation algorithm.
 * @param uM Measured Airspeed prior to the nav filter in m/s
 * @param phiC Commanded Roll
 * @param thetaC Commanded Pitch
 * @param psiDotC Commanded Turn rate
 * @param ayBody Y component of the body acceleration
 * @param totalDist Total Distance to Run on this leg of Navigation
 * @param dist2Go Remaining distance to Run on this leg of Navigation
 * @param fromWP Origin WP
 * @param toWP Destination WP
 * @param hC Commanded altitude in 0.1 m
 */
case class SlugsNavigation(uM: Float = 0, phiC: Float = 0, thetaC: Float = 0, psiDotC: Float = 0, ayBody: Float = 0, totalDist: Float = 0, dist2Go: Float = 0, fromWP: Byte = 0, toWP: Byte = 0, hC: Short = 0)
extends Message {
  val _id = 176
  val _name = "SLUGS_NAVIGATION"
  val _bundle = Bundle.slugs
  override def toString = "SLUGS_NAVIGATION" + " uM=" + uM + " phiC=" + phiC + " thetaC=" + thetaC + " psiDotC=" + psiDotC + " ayBody=" + ayBody + " totalDist=" + totalDist + " dist2Go=" + dist2Go + " fromWP=" + fromWP + " toWP=" + toWP + " hC=" + hC
}

/**
 * Configurable data log probes to be used inside Simulink
 * @param fl1 Log value 1
 * @param fl2 Log value 2
 * @param fl3 Log value 3
 * @param fl4 Log value 4
 * @param fl5 Log value 5
 * @param fl6 Log value 6
 */
case class DataLog(fl1: Float = 0, fl2: Float = 0, fl3: Float = 0, fl4: Float = 0, fl5: Float = 0, fl6: Float = 0)
extends Message {
  val _id = 177
  val _name = "DATA_LOG"
  val _bundle = Bundle.slugs
  override def toString = "DATA_LOG" + " fl1=" + fl1 + " fl2=" + fl2 + " fl3=" + fl3 + " fl4=" + fl4 + " fl5=" + fl5 + " fl6=" + fl6
}

/**
 * Pilot console PWM messges.
 * @param year Year reported by Gps
 * @param month Month reported by Gps
 * @param day Day reported by Gps
 * @param hour Hour reported by Gps
 * @param min Min reported by Gps
 * @param sec Sec reported by Gps
 * @param clockStat Clock Status. See table 47 page 211 OEMStar Manual
 * @param visSat Visible satellites reported by Gps
 * @param useSat Used satellites in Solution
 * @param gppgl GPS+GLONASS satellites in Solution
 * @param sigUsedMask GPS and GLONASS usage mask (bit 0 GPS_used? bit_4 GLONASS_used?)
 * @param percentUsed Percent used GPS
 */
case class GpsDateTime(year: Byte = 0, month: Byte = 0, day: Byte = 0, hour: Byte = 0, min: Byte = 0, sec: Byte = 0, clockStat: Byte = 0, visSat: Byte = 0, useSat: Byte = 0, gppgl: Byte = 0, sigUsedMask: Byte = 0, percentUsed: Byte = 0)
extends Message {
  val _id = 179
  val _name = "GPS_DATE_TIME"
  val _bundle = Bundle.slugs
  override def toString = "GPS_DATE_TIME" + " year=" + year + " month=" + month + " day=" + day + " hour=" + hour + " min=" + min + " sec=" + sec + " clockStat=" + clockStat + " visSat=" + visSat + " useSat=" + useSat + " gppgl=" + gppgl + " sigUsedMask=" + sigUsedMask + " percentUsed=" + percentUsed
}

/**
 * Mid Level commands sent from the GS to the autopilot. These are only sent when being operated in mid-level commands mode from the ground.
 * @param targetSystem The system setting the commands
 * @param hCommand Commanded Altitude in meters
 * @param uCommand Commanded Airspeed in m/s
 * @param rCommand Commanded Turnrate in rad/s
 */
case class MidLvlCmds(targetSystem: SystemId = 0, hCommand: Float = 0, uCommand: Float = 0, rCommand: Float = 0)
extends Message with TargetSystem[MidLvlCmds] {
  val _id = 180
  val _name = "MID_LVL_CMDS"
  val _bundle = Bundle.slugs
  def setTargetSystem(systemId: SystemId): MidLvlCmds = copy(targetSystem = systemId)
  override def toString = "MID_LVL_CMDS" + " targetSystem=" + targetSystem + " hCommand=" + hCommand + " uCommand=" + uCommand + " rCommand=" + rCommand
}

/**
 * This message sets the control surfaces for selective passthrough mode.
 * @param targetSystem The system setting the commands
 * @param bitfieldPt Bitfield containing the passthrough configuration, see CONTROL_SURFACE_FLAG ENUM.
 */
case class CtrlSrfcPt(targetSystem: SystemId = 0, bitfieldPt: Short = 0)
extends Message with TargetSystem[CtrlSrfcPt] {
  val _id = 181
  val _name = "CTRL_SRFC_PT"
  val _bundle = Bundle.slugs
  def setTargetSystem(systemId: SystemId): CtrlSrfcPt = copy(targetSystem = systemId)
  override def toString = "CTRL_SRFC_PT" + " targetSystem=" + targetSystem + " bitfieldPt=" + bitfieldPt
}

/**
 * Orders generated to the SLUGS camera mount.
 * @param targetSystem The system reporting the action
 * @param pan Order the mount to pan: -1 left, 0 No pan motion, +1 right
 * @param tilt Order the mount to tilt: -1 down, 0 No tilt motion, +1 up
 * @param zoom Order the zoom values 0 to 10
 * @param moveHome Orders the camera mount to move home. The other fields are ignored when this field is set. 1: move home, 0 ignored
 */
case class SlugsCameraOrder(targetSystem: SystemId = 0, pan: Byte = 0, tilt: Byte = 0, zoom: Byte = 0, moveHome: Byte = 0)
extends Message with TargetSystem[SlugsCameraOrder] {
  val _id = 184
  val _name = "SLUGS_CAMERA_ORDER"
  val _bundle = Bundle.slugs
  def setTargetSystem(systemId: SystemId): SlugsCameraOrder = copy(targetSystem = systemId)
  override def toString = "SLUGS_CAMERA_ORDER" + " targetSystem=" + targetSystem + " pan=" + pan + " tilt=" + tilt + " zoom=" + zoom + " moveHome=" + moveHome
}

/**
 * Control for surface; pending and order to origin.
 * @param targetSystem The system setting the commands
 * @param idSurface ID control surface send 0: throttle 1: aileron 2: elevator 3: rudder
 * @param mControl Pending
 * @param bControl Order to origin
 */
case class ControlSurface(targetSystem: SystemId = 0, idSurface: Byte = 0, mControl: Float = 0, bControl: Float = 0)
extends Message with TargetSystem[ControlSurface] {
  val _id = 185
  val _name = "CONTROL_SURFACE"
  val _bundle = Bundle.slugs
  def setTargetSystem(systemId: SystemId): ControlSurface = copy(targetSystem = systemId)
  override def toString = "CONTROL_SURFACE" + " targetSystem=" + targetSystem + " idSurface=" + idSurface + " mControl=" + mControl + " bControl=" + bControl
}

/**
 * Transmits the last known position of the mobile GS to the UAV. Very relevant when Track Mobile is enabled
 * @param targetSystem The system reporting the action
 * @param latitude Mobile Latitude
 * @param longitude Mobile Longitude
 */
case class SlugsMobileLocation(targetSystem: SystemId = 0, latitude: Float = 0, longitude: Float = 0)
extends Message with TargetSystem[SlugsMobileLocation] {
  val _id = 186
  val _name = "SLUGS_MOBILE_LOCATION"
  val _bundle = Bundle.slugs
  def setTargetSystem(systemId: SystemId): SlugsMobileLocation = copy(targetSystem = systemId)
  override def toString = "SLUGS_MOBILE_LOCATION" + " targetSystem=" + targetSystem + " latitude=" + latitude + " longitude=" + longitude
}

/**
 * Control for camara.
 * @param targetSystem The system setting the commands
 * @param idOrder ID 0: brightness 1: aperture 2: iris 3: ICR 4: backlight
 * @param order 1: up/on 2: down/off 3: auto/reset/no action
 */
case class SlugsConfigurationCamera(targetSystem: SystemId = 0, idOrder: Byte = 0, order: Byte = 0)
extends Message with TargetSystem[SlugsConfigurationCamera] {
  val _id = 188
  val _name = "SLUGS_CONFIGURATION_CAMERA"
  val _bundle = Bundle.slugs
  def setTargetSystem(systemId: SystemId): SlugsConfigurationCamera = copy(targetSystem = systemId)
  override def toString = "SLUGS_CONFIGURATION_CAMERA" + " targetSystem=" + targetSystem + " idOrder=" + idOrder + " order=" + order
}

/**
 * Transmits the position of watch
 * @param targetSystem The system reporting the action
 * @param latitude ISR Latitude
 * @param longitude ISR Longitude
 * @param height ISR Height
 * @param option1 Option 1
 * @param option2 Option 2
 * @param option3 Option 3
 */
case class IsrLocation(targetSystem: SystemId = 0, latitude: Float = 0, longitude: Float = 0, height: Float = 0, option1: Byte = 0, option2: Byte = 0, option3: Byte = 0)
extends Message with TargetSystem[IsrLocation] {
  val _id = 189
  val _name = "ISR_LOCATION"
  val _bundle = Bundle.slugs
  def setTargetSystem(systemId: SystemId): IsrLocation = copy(targetSystem = systemId)
  override def toString = "ISR_LOCATION" + " targetSystem=" + targetSystem + " latitude=" + latitude + " longitude=" + longitude + " height=" + height + " option1=" + option1 + " option2=" + option2 + " option3=" + option3
}

/**
 * Transmits the readings from the voltage and current sensors
 * @param r2Type It is the value of reading 2: 0 - Current, 1 - Foreward Sonar, 2 - Back Sonar, 3 - RPM
 * @param voltage Voltage in uS of PWM. 0 uS = 0V, 20 uS = 21.5V
 * @param reading2 Depends on the value of r2Type (0) Current consumption in uS of PWM, 20 uS = 90Amp (1) Distance in cm (2) Distance in cm (3) Absolute value
 */
case class VoltSensor(r2Type: Byte = 0, voltage: Short = 0, reading2: Short = 0)
extends Message {
  val _id = 191
  val _name = "VOLT_SENSOR"
  val _bundle = Bundle.slugs
  override def toString = "VOLT_SENSOR" + " r2Type=" + r2Type + " voltage=" + voltage + " reading2=" + reading2
}

/**
 * Transmits the actual Pan, Tilt and Zoom values of the camera unit
 * @param zoom The actual Zoom Value
 * @param pan The Pan value in 10ths of degree
 * @param tilt The Tilt value in 10ths of degree
 */
case class PtzStatus(zoom: Byte = 0, pan: Short = 0, tilt: Short = 0)
extends Message {
  val _id = 192
  val _name = "PTZ_STATUS"
  val _bundle = Bundle.slugs
  override def toString = "PTZ_STATUS" + " zoom=" + zoom + " pan=" + pan + " tilt=" + tilt
}

/**
 * Transmits the actual status values UAV in flight
 * @param targetSystem The ID system reporting the action
 * @param latitude Latitude UAV
 * @param longitude Longitude UAV
 * @param altitude Altitude UAV
 * @param speed Speed UAV
 * @param course Course UAV
 */
case class UavStatus(targetSystem: SystemId = 0, latitude: Float = 0, longitude: Float = 0, altitude: Float = 0, speed: Float = 0, course: Float = 0)
extends Message with TargetSystem[UavStatus] {
  val _id = 193
  val _name = "UAV_STATUS"
  val _bundle = Bundle.slugs
  def setTargetSystem(systemId: SystemId): UavStatus = copy(targetSystem = systemId)
  override def toString = "UAV_STATUS" + " targetSystem=" + targetSystem + " latitude=" + latitude + " longitude=" + longitude + " altitude=" + altitude + " speed=" + speed + " course=" + course
}

/**
 * This contains the status of the GPS readings
 * @param csFails Number of times checksum has failed
 * @param gpsQuality The quality indicator, 0=fix not available or invalid, 1=GPS fix, 2=C/A differential GPS, 6=Dead reckoning mode, 7=Manual input mode (fixed position), 8=Simulator mode, 9= WAAS a
 * @param msgsType Indicates if GN, GL or GP messages are being received
 * @param posStatus A = data valid, V = data invalid
 * @param magVar Magnetic variation, degrees
 * @param magDir Magnetic variation direction E/W. Easterly variation (E) subtracts from True course and Westerly variation (W) adds to True course
 * @param modeInd Positioning system mode indicator. A - Autonomous;D-Differential; E-Estimated (dead reckoning) mode;M-Manual input; N-Data not valid
 */
case class StatusGps(csFails: Short = 0, gpsQuality: Byte = 0, msgsType: Byte = 0, posStatus: Byte = 0, magVar: Float = 0, magDir: Byte = 0, modeInd: Byte = 0)
extends Message {
  val _id = 194
  val _name = "STATUS_GPS"
  val _bundle = Bundle.slugs
  override def toString = "STATUS_GPS" + " csFails=" + csFails + " gpsQuality=" + gpsQuality + " msgsType=" + msgsType + " posStatus=" + posStatus + " magVar=" + magVar + " magDir=" + magDir + " modeInd=" + modeInd
}

/**
 * Transmits the diagnostics data from the Novatel OEMStar GPS
 * @param timeStatus The Time Status. See Table 8 page 27 Novatel OEMStar Manual
 * @param receiverStatus Status Bitfield. See table 69 page 350 Novatel OEMstar Manual
 * @param solStatus solution Status. See table 44 page 197
 * @param posType position type. See table 43 page 196
 * @param velType velocity type. See table 43 page 196
 * @param posSolAge Age of the position solution in seconds
 * @param csFails Times the CRC has failed since boot
 */
case class NovatelDiag(timeStatus: Byte = 0, receiverStatus: Int = 0, solStatus: Byte = 0, posType: Byte = 0, velType: Byte = 0, posSolAge: Float = 0, csFails: Short = 0)
extends Message {
  val _id = 195
  val _name = "NOVATEL_DIAG"
  val _bundle = Bundle.slugs
  override def toString = "NOVATEL_DIAG" + " timeStatus=" + timeStatus + " receiverStatus=" + receiverStatus + " solStatus=" + solStatus + " posType=" + posType + " velType=" + velType + " posSolAge=" + posSolAge + " csFails=" + csFails
}

/**
 * Diagnostic data Sensor MCU
 * @param float1 Float field 1
 * @param float2 Float field 2
 * @param int1 Int 16 field 1
 * @param char1 Int 8 field 1
 */
case class SensorDiag(float1: Float = 0, float2: Float = 0, int1: Short = 0, char1: Byte = 0)
extends Message {
  val _id = 196
  val _name = "SENSOR_DIAG"
  val _bundle = Bundle.slugs
  override def toString = "SENSOR_DIAG" + " float1=" + float1 + " float2=" + float2 + " int1=" + int1 + " char1=" + char1
}

/**
 * The boot message indicates that a system is starting. The onboard software version allows to keep track of onboard soft/firmware revisions. This message allows the sensor and control MCUs to communicate version numbers on startup.
 * @param version The onboard software version
 */
case class Boot(version: Int = 0)
extends Message {
  val _id = 197
  val _name = "BOOT"
  val _bundle = Bundle.slugs
  override def toString = "BOOT" + " version=" + version
}
