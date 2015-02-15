package funnel {

  import scala.concurrent.duration._

  package object instruments extends Instruments(1 minute) with DefaultKeys {
    implicit val log: String => Unit = s => Monitoring.default.log(s)

    Clocks.instrument(this)
    JVM.instrument(this)
    Sigar(this).foreach { _.instrument }
  }
}

import scalaz.concurrent.Task
import scalaz.stream.Process

package object funnel {
  type DatapointParser = java.net.URI => Process[Task,Datapoint[Any]]
  type BucketName      = String
}
