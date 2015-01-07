package scavlink.coord

/**
 * Base trait for 3D coordinates.
 */
trait XYZ[T] {
  def x: T
  def y: T
  def z: T
  def toVector = Vector(x, y, z)
  override def toString = s"($x,$y,$z)"
}
