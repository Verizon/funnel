package oncue.svc.funnel
package agent

case class ArbitraryMetric(
  name: String,
  kind: InstrumentKind,
  value: Option[String]
)
