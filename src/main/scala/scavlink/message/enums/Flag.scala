package scavlink.message.enums

trait Flag {
  self: Enumeration =>

  def maskToSet(value: Int): ValueSet = for (v <- this.values if (value & v.id) > 0) yield v

  def setToMask(vs: ValueSet): Int = vs.foldLeft(0)(_ | _.id)
}
