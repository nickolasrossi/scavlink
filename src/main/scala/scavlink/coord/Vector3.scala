package scavlink.coord

/**
 * Just a basic 3D vector.
 * Methods added here that are not provided by Spire.
 */
case class Vector3(x: Double = 0, y: Double = 0, z: Double = 0) extends XYZ[Double] {
  lazy val length: Double = math.sqrt(x * x + y * y + z * z)

  def cross(that: Vector3) = Vector3(
    this.y * that.z - this.z * that.y,
    this.z * that.x - this.x * that.z,
    this.x * that.y - this.y * that.x
  )
}
