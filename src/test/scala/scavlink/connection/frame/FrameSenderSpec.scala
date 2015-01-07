package scavlink.connection.frame

import scavlink.message._
import org.scalatest.FlatSpec

class FrameSenderSpec extends FlatSpec with FrameTestData {

  "buildPacket" should "produce valid sequential frames from messages" in {
    val framer = new FrameSender(1, 1, Sequence(29))
    val f = framer.nextMessage(Bundle.common.marshaller) _

    assert(heartbeatData(29) === f(heartbeatMsg(29)))
    assert(heartbeatData(30) === f(heartbeatMsg(30)))
    assert(heartbeatData(31) === f(heartbeatMsg(31)))
  }
}
