package intelmedia.ws.commons
package monitoring

/**
 * A value of type `A`, constained to be either
 * an `Int`, `Double`, or `String`.
 */
sealed trait Reportable[+A] { def get: A }

object Reportable {
  case class I(get: Int) extends Reportable[Int]
  case class B(get: Boolean) extends Reportable[Boolean]
  case class D(get: Double) extends Reportable[Double]
  case class S(get: String) extends Reportable[String]
  case class Stats(get: monitoring.Stats) extends Reportable[monitoring.Stats]
  case class Histogram(get: monitoring.Histogram[String]) extends Reportable[monitoring.Histogram[String]]

  implicit def int(a: Int): Reportable[Int] = I(a)
  implicit def boolean(a: Boolean): Reportable[Boolean] = B(a)
  implicit def double(a: Double): Reportable[Double] = D(a)
  implicit def string(a: String): Reportable[String] = S(a)
  implicit def stats(a: monitoring.Stats): Reportable[monitoring.Stats] = Stats(a)
  implicit def histogram(a: monitoring.Histogram[String]): Reportable[monitoring.Histogram[String]] = Histogram(a)

  def apply[A](a: A)(implicit toReportable: A => Reportable[A]): Reportable[A] =
    toReportable(a)
}
