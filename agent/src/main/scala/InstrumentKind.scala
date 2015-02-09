package oncue.svc.funnel
package agent

trait InstrumentKind
object InstrumentKinds {
  case object Counter extends InstrumentKind
  case object Timer extends InstrumentKind
  case object GaugeString extends InstrumentKind
  case object GaugeDouble extends InstrumentKind
}