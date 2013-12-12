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

  package object monitoring {
    type DatapointParser = java.net.URL => Process[Task,Datapoint[Any]]
  }

}
