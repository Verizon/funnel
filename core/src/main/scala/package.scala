package funnel {

  import scala.concurrent.duration._

  package object instruments extends Instruments(1 minute) with DefaultKeys {

    Clocks.instrument(this)
    JVM.instrument(this)
    Sigar(this).foreach { _.instrument }
  }
}

import scalaz.concurrent.Task
import scalaz.stream.Process

package object funnel {
  type DatapointParser    = java.net.URI => Process[Task,Datapoint[Any]]
  type ClusterName        = String
  type ContinuousGauge[A] = Gauge[Continuous[A],A]
}
