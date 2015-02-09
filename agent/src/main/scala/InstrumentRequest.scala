package oncue.svc.funnel
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
}
