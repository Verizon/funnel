package intelmedia.ws.funnel

// as we deal with a bunch of side effects, lets use
// out own "unit" in order to avoid the implicit coercision of
// values to Unit.
sealed class SafeUnit
object SafeUnit {
  val Safe = new SafeUnit
  implicit def unitToSafeUnit(in: Unit): SafeUnit = Safe
  implicit def safeUnitToUnit(in: SafeUnit): Unit = ()
}

