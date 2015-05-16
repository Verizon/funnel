package oncue.svc.funnel {

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
import scalaz.Monoid
import com.twitter.algebird.Group

package object funnel {
  type DatapointParser    = java.net.URI => Process[Task,Datapoint[Any]]
  type ClusterName        = String
  type ContinuousGauge[A] = Gauge[Continuous[A],A]

  implicit def GroupMonoid[A](implicit G: Group[A]): Monoid[A] = new Monoid[A] {
    val zero = G.zero
    def append(a: A, b: => A) = G.plus(a, b)
  }
}
