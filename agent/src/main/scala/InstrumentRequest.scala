package oncue.svc.funnel
package agent

case class InstrumentRequest(
  cluster: String,
  counters: List[ArbitraryMetric] = Nil,
  timers: List[ArbitraryMetric] = Nil,
  stringGauges: List[ArbitraryMetric] = Nil,
  doubleGauges: List[ArbitraryMetric] = Nil
)