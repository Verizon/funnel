package funnel

import scala.concurrent.duration._
import com.twitter.algebird.Group
import scalaz.Free._
import scalaz.syntax.applicative._

object Metric {
  /**
   * Combines two periodic instruments with keys `a` and `b` into one metric
   * using the function `f`.
   */
  def combinePeriodic[A:Reportable,B:Reportable,C:Reportable:Group](
    a: Periodic[A],
    b: Periodic[B],
    label: String,
    units: Units,
    description: String = "",
    M: Monitoring = Monitoring.default,
    keyMod: Key[C] => Key[C] = identity[Key[C]] _)(f: (A, B) => C): Periodic[C] = {
      val C = implicitly[Group[C]]
      val (ks, _) =
        instruments.periodicBuffers(Buffers.sum(GroupMonoid(C)), (c: C) => c, label, units, description, keyMod)
      val metrics = a.toThree.zipWith(b.toThree) {
        case (k1, k2) => for { x <- liftFC(k1); y <- liftFC(k2) } yield f(x, y)
      }
      Periodic(ks.toThree.zip(metrics).map {
        case (k, m) => M.publish(k)(Events.changed(k))(m).run
      })
  }

  /**
   * Combines two periodic instruments with keys `a` and `b` into one metric
   * using the function `f`.
   */
  def combineInstruments[A:Reportable,B:Reportable,C:Reportable:Group](
    a: Instrument[Periodic[A]],
    b: Instrument[Periodic[B]],
    label: String,
    units: Units,
    description: String = "",
    M: Monitoring = Monitoring.default,
    keyMod: Key[C] => Key[C])(f: (A, B) => C) {
      combinePeriodic(a.keys, b.keys, label, units, description, M, keyMod)(f)
  }

  /** Infix syntax for `Metric`. */
  implicit class MetricSyntax[A](self: Metric[A]) {
    import Events.Event

    /** Publish this `Metric` to `M` whenever `ticks` emits a value. */
    def publish(ticks: Event)(label: String, units: Units = Units.None)(
                implicit R: Reportable[A],
                M: Monitoring = Monitoring.default): Key[A] =
      M.publish(label, units)(ticks)(self).run

    /** Publish this `Metric` to `M` every `d` elapsed time. */
    def publishEvery(d: Duration)(label: String, units: Units = Units.None)(
                     implicit R: Reportable[A],
                     M: Monitoring = Monitoring.default): Key[A] =
      publish(Events.every(d))(label, units)

    /** Publish this `Metric` to `M` when `k` is updated. */
    def publishOnChange(k: Key[Any])(label: String, units: Units = Units.None)(
                        implicit R: Reportable[A],
                        M: Monitoring = Monitoring.default): Key[A] =
      publish(Events.changed(k))(label, units)

    /** Publish this `Metric` to `M` when either `k` or `k2` is updated. */
    def publishOnChanges(k: Key[Any], k2: Key[Any])(
                         label: String, units: Units = Units.None)(
                         implicit R: Reportable[A],
                         M: Monitoring = Monitoring.default): Key[A] =
      publish(Events.or(Events.changed(k), Events.changed(k2)))(label, units)
  }
}
