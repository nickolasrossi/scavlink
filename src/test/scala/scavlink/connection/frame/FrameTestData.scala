package scavlink.connection.frame

import akka.util.ByteString
import scavlink.message.common.Heartbeat
import scavlink.message.{From, Packet, VehicleId}

trait FrameTestData extends Framing {
  val address = "mock"

  val ardupilotBundleData1 = ByteString(254, 44, 57, 1, 1, 164, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 10, 232, 28, 193, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 70, 55, 107, 28, 142, 94, 22, 183, 31, 64)
  val ardupilotBundleData2 = ByteString(254, 24, 58, 1, 1, 178, 188, 103, 39, 60, 205, 187, 73, 60, 95, 107, 156, 61, 123, 20, 82, 66, 55, 55, 107, 28, 155, 94, 22, 183, 204, 140)

  val garbledData = ByteString(0, 45, 66, 163, 118, 72, 33, 3, 1, 7, 254, 7, 111, 24, 87, 46, 201, 78, 5, 0)

  def heartbeatMsg(n: Int) = Heartbeat(2, 3, 81, n, 3, 3)

  def heartbeatPacket(n: Int) = Packet(From(VehicleId.fromLink(address, 1), 1, 1), heartbeatMsg(n))(Sequence(n))

  // a unique heartbeat packet for every possible sequence number 0 through 255, where customMode == seq
  val heartbeatData = Vector.tabulate[ByteString](256) { n =>
    val data = ByteString(-2, 9, n, 1, 1, 0, n, 0, 0, 0, 2, 3, 81, 3, 3)
    val (crcLo, crcHi) = crcBytes(computeCrc(data.drop(1).iterator, 50))
    data ++ ByteString(crcLo, crcHi)
  }
}
