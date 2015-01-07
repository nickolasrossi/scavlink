package scavlink.log

import scavlink.connection.frame.DefaultMarshallerFactory
import org.scalatest.{Matchers, WordSpec}

class LogFileReaderSpec extends WordSpec with Matchers {

  "readLog" should {
    "read packets from a flight log" in {
      val logReader = new LogFileReader(DefaultMarshallerFactory.apply, getClass.getResourceAsStream("/flight.tlog"))
      val packets = logReader.readLog()
      // TODO: more meaningful test case
      packets.size shouldBe 1467
    }

  }
}
