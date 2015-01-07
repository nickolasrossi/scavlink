package scavlink.connection.marshal

import scavlink.message.{Bundle, Message}
import akka.util.{ByteIterator, ByteString, ByteStringBuilder}

/**
 * A marshaller knows how to convert a Message to bytes and vice-versa.
 * The list of magic numbers defines which message ids the marshaller knows how to convert.
 */
trait MessageMarshaller {
  def magic: PartialFunction[Int, Int]
  def marshal: PartialFunction[Message, ByteString]
  def unmarshal: PartialFunction[(Int, ByteIterator), Message]

  /**
   * Combine the message conversion of two marshallers into a single one.
   */
  def orElse(that: MessageMarshaller) =
    if (that == this) this else new MessageMarshaller {
      def magic = MessageMarshaller.this.magic orElse that.magic
      def marshal = MessageMarshaller.this.marshal orElse that.marshal
      def unmarshal = MessageMarshaller.this.unmarshal orElse that.unmarshal
    }
}

/**
 * Marshaller for a single bundle (corresponding to a single xml definition from the MAVLink source).
 */
trait BundleMarshaller extends MessageMarshaller {
  def bundle: Bundle

  def _marshal(message: Message)(implicit builder: ByteStringBuilder): Unit
  def _unmarshal(msgId: Int)(implicit iter: ByteIterator): Message

  def marshal = new PartialFunction[Message, ByteString] {
    def isDefinedAt(x: Message) = magic.isDefinedAt(x._id)
    def apply(x: Message) = {
      implicit val builder = new ByteStringBuilder
      _marshal(x)
      builder.result()
    }
  }

  def unmarshal = new PartialFunction[(Int, ByteIterator), Message] {
    def isDefinedAt(x: (Int, ByteIterator)) = magic.isDefinedAt(x._1)
    def apply(x: (Int, ByteIterator)) = {
      val (msgId, payload) = x
      implicit val iter = payload
      _unmarshal(msgId)
    }
  }
}
