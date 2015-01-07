package scavlink.connection.serial

case class Options(bitRate: Int, dataBits: DataBits.Value = DataBits._8,
                   parity: Parity.Value = Parity.None, stopBits: StopBits.Value = StopBits._1) {
  override def toString = bitRate + "/" + dataBits + parity + stopBits
}

object DataBits extends Enumeration {
  val _8 = Value(8, "8")
  val _7 = Value(7, "7")
  val _6 = Value(6, "6")
  val _5 = Value(5, "5")
}

object StopBits extends Enumeration {
  val _1 = Value(1, "1")
  val _2 = Value(2, "2")
  val _1_5 = Value(3, "1.5")
}

object Parity extends Enumeration {
  val None = Value(0, "N")
  val Odd = Value(1, "O")
  val Even = Value(2, "E")
  val Mark = Value(3, "M")
  val Space = Value(4, "S")
}
