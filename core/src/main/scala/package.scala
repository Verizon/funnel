package oncue.svc.funnel {

  import scala.concurrent.duration._

  package object instruments extends Instruments(1 minute, Monitoring.default) with DefaultKeys {
    implicit val log: String => Unit = s => Monitoring.default.log(s)

    Clocks.instrument(this)
    JVM.instrument(this)
    Sigar(this).foreach { _.instrument }
  }

}

package oncue.svc {

  import scalaz.concurrent.Task
  import scalaz.stream.Process

  package object funnel {
    type DatapointParser = java.net.URL => Process[Task,Datapoint[Any]]
    type BucketName = String
  }

}
