package scavlink.log

import java.io.{BufferedOutputStream, File, FileOutputStream, OutputStream}

import scavlink.settings.SettingsCompanion
import akka.actor.{ReceiveTimeout, Actor, Props}
import akka.util.ByteString
import com.typesafe.config.Config
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

import scala.concurrent.duration._

object LinkLoggingActor {
  def props(name: String, path: String) = Props(classOf[LinkLoggingActor], name, path)  
}

/**
 * Logs raw data received on a link.
 * Log files should be equivalent to those produced by ground control apps such as mavproxy.
 */
class LinkLoggingActor(name: String, path: String) extends Actor {
  private val output = openFile(name, path)
  context.setReceiveTimeout(3.seconds)

  override def postStop() = output.close()

  def receive = {
    case data: ByteString => output.write(data.toByteBuffer.array())
    case ReceiveTimeout => output.flush()
  }

  private def openFile(name: String, path: String): OutputStream = {
    val dir = new File(path, name)
    dir.mkdirs()

    val fmt = DateTimeFormat.forPattern("yyyyMMDD-HHmmss")
    val start = DateTime.now.toString(fmt)

    new BufferedOutputStream(new FileOutputStream(new File(dir, s"${name}_$start.log")), 1024)
  }
}


case class LogSettings(isEnabled: Boolean, path: String)

object LogSettings extends SettingsCompanion[LogSettings]("log") {
  def fromSubConfig(config: Config) = LogSettings(config.getBoolean("enabled"), config.getString("path"))
}
