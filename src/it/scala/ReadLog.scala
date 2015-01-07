import java.io.{File, FileInputStream}

import scavlink.connection.frame.DefaultMarshallerFactory
import scavlink.log.LogFileReader

object ReadLog extends App {
  val file = new File(args(0))
  val reader = new LogFileReader(DefaultMarshallerFactory.apply, new FileInputStream(file))
  reader.readLog() foreach println
}
