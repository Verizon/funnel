package intelmedia.ws
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

  implicit def reportableInt(a: Int): Reportable[Int] = I(a)
  implicit def reportableBoolean(a: Boolean): Reportable[Boolean] = B(a)
  implicit def reportableDouble(a: Double): Reportable[Double] = D(a)
  implicit def reportableString(a: String): Reportable[String] = S(a)
  implicit def reportableStats(a: monitoring.Stats): Reportable[monitoring.Stats] = Stats(a)
  implicit def reportableHistogram(a: monitoring.Histogram[String]): Reportable[monitoring.Histogram[String]] = Histogram(a)

  def apply[A](a: A)(implicit toReportable: A => Reportable[A]): Reportable[A] =
    toReportable(a)
}
