package intelmedia.ws.funnel {

  import scala.concurrent.duration._

  package object instruments extends Instruments(5 minutes, Monitoring.default) with DefaultKeys {
    JVM.instrument(this)
    Clocks.instrument(this)
  }

}

package intelmedia.ws {

  import scalaz.concurrent.Task
  import scalaz.stream.Process

  package object funnel {
    type DatapointParser = java.net.URL => Process[Task,Datapoint[Any]]

    // as we deal with a bunch of side effects, lets use
    // out own "unit" in order to avoid the implicit coercision of
    // values to Unit.
    sealed class SafeUnit
    val Safe = new SafeUnit
    implicit def unitToSafeUnit(in: Unit): SafeUnit = Safe
    implicit def safeUnitToUnit(in: SafeUnit): Unit = ()
  }

}
