package funnel
package agent

case class InstrumentRequest(
  cluster: String,
  counters: List[ArbitraryMetric] = Nil,
  timers: List[ArbitraryMetric] = Nil,
  stringGauges: List[ArbitraryMetric] = Nil,
  doubleGauges: List[ArbitraryMetric] = Nil
)
object InstrumentRequest {
  import InstrumentKinds._

  def apply(cluster: String, metric: ArbitraryMetric): InstrumentRequest =
    metric.kind match {
      case Counter     => InstrumentRequest(cluster, counters = metric :: Nil)
      case Timer       => InstrumentRequest(cluster, timers = metric :: Nil)
      case GaugeDouble => InstrumentRequest(cluster, doubleGauges = metric :: Nil)
    }

  def apply(cluster: String, metrics: ArbitraryMetric*): InstrumentRequest =
    InstrumentRequest(cluster,
      counters     = metrics.filter(_.kind == Counter).toList,
      timers       = metrics.filter(_.kind == Timer).toList,
      stringGauges = metrics.filter(_.kind == GaugeString).toList,
      doubleGauges = metrics.filter(_.kind == GaugeDouble).toList
    )
}
