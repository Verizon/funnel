package oncue.svc.funnel

class Counter(underlying: PeriodicGauge[Double]) extends Instrument[Periodic[Double]] { self =>
  def incrementBy(by: Int): Unit = underlying append by
  def increment: Unit = incrementBy(1)
  def keys = underlying.keys
}
