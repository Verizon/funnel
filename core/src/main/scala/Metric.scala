package funnel

import scala.concurrent.duration._

object Metric {
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
