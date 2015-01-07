package scavlink.connection.frame

/**
 * Packet sequence value that wraps around at 255.
 */
class Sequence private(val value: Int) extends AnyVal with Ordered[Sequence] {
  def next: Sequence = this + 1
  def prev: Sequence = this - 1

  def +(n: Int): Sequence = new Sequence((value + n) % 256)

  def -(n: Int): Sequence = {
    val nv = value - n
    if (nv < 0) {
      new Sequence(256 + (nv % 256))
    } else {
      new Sequence(nv)
    }
  }

  def compare(that: Sequence): Int = {
    val v = (this - that.value).value
    if (v >= 128) v - 256 else v
  }

  override def toString = value.toString
}

object Sequence {
  def apply(value: Int): Sequence = new Sequence(value % 256)
  def zero = new Sequence(0)
}
