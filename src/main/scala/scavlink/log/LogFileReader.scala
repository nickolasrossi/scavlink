package scavlink.log

import java.io.InputStream

import scavlink.connection.frame.FrameReceiver
import scavlink.connection.marshal._
import scavlink.message.Packet
import akka.util.ByteString

import scalax.io.Resource
import scalax.io.managed.InputStreamResource

/**
 * Iterates the contents of a log file as a sequence of Packet objects.
 * @author kkuch
 */
class LogFileReader(marshallerFactory: MarshallerFactory, in: InputStream) extends Iterable[Packet] {
  private val rx = new FrameReceiver("log", marshallerFactory)

  /**
   * Reads the contents of a log file into a sequence of Packet objects.
   * @return a sequence containing the Packet objects read from the log file
   */
  def readLog(): Seq[Packet] = iterator.toSeq

  override def iterator: Iterator[Packet] = new PacketIterator(Resource.fromInputStream(in))

  /**
   * Iterates the contents of a log file as a sequence of Packet objects
   */
  class PacketIterator(resource: InputStreamResource[InputStream]) extends Iterator[Packet] {

    private val rowTransformer = for {
      processor <- resource.bytes.processor
      _ <- processor.repeatUntilEmpty()
      rowData <- processor.take(32)
    } yield {
      ByteString(rowData.toArray)
    }

    private val rowIterator : Iterator[ByteString] = rowTransformer.traversable[ByteString].toIterator
    private var packets : Iterator[Packet] = List.empty.iterator

    override def hasNext: Boolean = packets.hasNext || rowIterator.hasNext

    override def next(): Packet = {
      if (packets.hasNext) packets.next() else readNext
    }

    def readNext: Packet = {
      if (!rowIterator.hasNext) throw new NoSuchElementException

      val data = rowIterator.map(rx.receivedData).flatten
      packets = data.collect { case Right(packet) => packet }.toIterator
      next()
    }
  }
}
