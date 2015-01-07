package scavlink.connection.frame

import scavlink.connection.marshal._
import scavlink.message.Bundle
import akka.util.ByteString
import org.scalatest.{Matchers, WordSpec}

import scala.language.reflectiveCalls

class FrameReceiverSpec extends WordSpec with Matchers with FrameTestData {

  def fixture = new {
    val framer = new FrameReceiver(address, DefaultMarshallerFactory.apply)
  }

  "computeCrc" should {
    "compute a packet CRC that agrees with the last two bytes of the frame" in {
      val f = assertCrc(Bundle.common.marshaller) _
      f(heartbeatData(29))
      f(heartbeatData(30))
      f(heartbeatData(31))
    }

    "compute a valid CRC for messages not in the common bundle" in {
      val f = assertCrc(Bundle.ardupilotmega.marshaller) _
      f(ardupilotBundleData1)
      f(ardupilotBundleData2)
    }

    "compute a valid CRC for both a common and another bundle message" in {
      val marshaller = Bundle.common.marshaller orElse Bundle.ardupilotmega.marshaller
      val f = assertCrc(marshaller) _
      f(heartbeatData(29))
      f(ardupilotBundleData1)
    }

    def assertCrc(marshaller: MessageMarshaller)(data: ByteString) = {
      val f = fixture
      val crc = f.framer.computeCrc(data.iterator.slice(1, data.length - 2),
        marshaller.magic(f.framer.unsigned(data(5))))
      extractCrc(data) shouldBe crc
    }

    def extractCrc(data: ByteString): Int =
      (data(data.length - 2) & 0xff) + ((data(data.length - 1) & 0xff) << 8)
  }

  "unmarshal" should {

    "extract a packet from raw data" in {
      val f = fixture

      val (newData, packets) = f.framer.unmarshal(heartbeatData(29), Nil)
      assert(newData.isEmpty)
      packets.length shouldBe 1
      packets.head shouldBe Right(heartbeatPacket(29))
    }

    "skip over garbage data to extract valid packets based on the frame start value" in {
      val f = fixture

      val (data1, packets1) = f.framer.unmarshal(garbledData, Nil)
      packets1.length shouldBe 0
      data1.head shouldBe f.framer.FrameStart
      data1.length shouldBe garbledData.length - garbledData.indexOf(f.framer.FrameStart)

      val (data2, packets2) = f.framer.unmarshal(data1 ++ garbledData, Nil)
      packets2.length shouldBe 1
      packets2.head shouldBe 'left
      data2.head shouldBe f.framer.FrameStart
      data2.length shouldBe garbledData.length - garbledData.indexOf(f.framer.FrameStart)

      val withGoodPackets = data2 ++ heartbeatData(29) ++ heartbeatData(30) ++ garbledData
      val (data3, packets3) = f.framer.unmarshal(withGoodPackets, Nil)
      packets3.length shouldBe 3
      packets3(0) shouldBe 'left
      packets3(1) shouldBe Right(heartbeatPacket(29))
      packets3(2) shouldBe Right(heartbeatPacket(30))

      data3.head shouldBe f.framer.FrameStart
      data3.length shouldBe garbledData.length - garbledData.indexOf(f.framer.FrameStart)
    }
  }

  "receivedData" should {
    "extract packets across a series of buffers that aren't aligned on the packets" in {
      val f = fixture

      val data1 = heartbeatData(29).take(7)
      val data2 = heartbeatData(29).drop(7) ++ heartbeatData(30).take(3)
      val data3 = heartbeatData(30).slice(3, 10)
      val data4 = heartbeatData(30).drop(10) ++ heartbeatData(31) ++ heartbeatData(32).take(6)

      val packets1 = f.framer.receivedData(data1)
      assert(packets1.isEmpty)

      val packets2 = f.framer.receivedData(data2)
      packets2.length shouldBe 1
      packets2(0) shouldBe Right(heartbeatPacket(29))

      val packets3 = f.framer.receivedData(data3)
      assert(packets3.isEmpty)

      val packets4 = f.framer.receivedData(data4)
      packets4.length shouldBe 2
      packets4(0) shouldBe Right(heartbeatPacket(30))
      packets4(1) shouldBe Right(heartbeatPacket(31))
    }
  }
}
