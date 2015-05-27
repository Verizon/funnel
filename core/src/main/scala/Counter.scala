package oncue.svc.funnel

class Counter(underlying: PeriodicGauge[Double]) extends Instrument[Periodic[Double]] { self =>
  def incrementBy(by: Int): Unit = underlying append by
  def increment: Unit = incrementBy(1)

  @deprecated("Counters should be monotonically increasing. Use a numericGauge instead.", "2.2")
  def decrementBy(by: Int): Unit = incrementBy(-by)

  @deprecated("Counters should be monotonically increasing. Use a numericGauge instead.", "2.2")
  def decrement: Unit = decrementBy(1)
  def keys = underlying.keys
}
