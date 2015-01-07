package scavlink.test.map

/**
 * From [http://otfried-cheong.appspot.com/scala/timers.html]
 */
object Timer {
  def apply(interval: Int, repeats: Boolean = false)(op: => Unit) {
    val timeOut = new javax.swing.AbstractAction() {
      def actionPerformed(e: java.awt.event.ActionEvent) = op
    }

    val t = new javax.swing.Timer(interval, timeOut)
    t.setRepeats(repeats)
    t.start()
  }
}