package scavlink.link.nav

import spire.implicits._

/**
 * Non-parametric regression method that is more robust to outliers than least squares.
 * [[http://www.chem.uoa.gr/applets/AppletTheil/Appl_Theil2.html]]
 * @author Nick Rossi
 */
trait TheilIncompleteDistance {
  def computeChangeRate(distances: Distances): Double = {
    val m = distances.length / 2
    val low = distances.take(m)
    val high = distances.takeRight(m)
    val slopes = low.zip(high).map { case ((t1, d1), (t2, d2)) =>
      (d2 - d1) / (t2 - t1).toDouble
    }

    median(slopes)
  }
}
