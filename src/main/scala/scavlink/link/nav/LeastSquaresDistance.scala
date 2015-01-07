package scavlink.link.nav

import Jama.Matrix

import scala.util.Try

/**
 * Simple linear regression over a sample of distance calculations.
 *
 * TODO: Would rather use Breeze, but its pinv() function throws an exception for all singular matrices:
 * [[https://github.com/scalanlp/breeze/issues/304]]
 * @author Nick Rossi
 */
trait LeastSquaresDistance {

  def computeChangeRate(distances: Distances): Double = regress(distances)._2

  /**
   * Regress (time, distance) pairs into an equation of the form d(t) = d0 + rt
   * @param distances map of time to distance
   * @return (initial distance d0, distance change rate r)
   */
  def regress(distances: Distances): (Double, Double) = {
    if (distances.isEmpty) {
      (Double.NaN, Double.NaN)
    } else {
      val (t, d) = matrices(distances)
      Try(linreg(t, d)).getOrElse((Double.NaN, Double.NaN))
    }
  }

  def linreg(T: Matrix, D: Matrix): (Double, Double) = {
    val Tt = T.transpose
    val r = Tt.times(T).inverse().times(Tt).times(D)
    (r.get(0, 0), r.get(1, 0))
  }

  def matrices(distances: Distances): (Matrix, Matrix) = {
    val dsize = distances.size
    val T = new Array[Array[Double]](dsize)
    val D = new Array[Array[Double]](dsize)

    val iter = distances.iterator
    var i = 0
    while (iter.hasNext) {
      val (t, d) = iter.next()
      T(i) = Array[Double](1, t)
      D(i) = Array[Double](d)
      i += 1
    }

    (new Matrix(T), new Matrix(D))
  }
}
