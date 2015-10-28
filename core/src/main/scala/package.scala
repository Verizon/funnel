package funnel {

  package object instruments extends Instruments(funnel.defaultWindow) with DefaultKeys {
    val instance = this

    Clocks.instrument(this)
    JVM.instrument(this)
    Sigar(this).foreach { _.instrument }
  }
}

import scalaz.Free.FreeC
import scalaz.concurrent.Task
import scalaz.stream.Process
import scalaz.Monoid
import com.twitter.algebird.Group

package object funnel {
  import scala.concurrent.duration._

  type KeySet[A] = OneOrThree[Key[A]]
  type Metric[A] = FreeAp[KeySet,A]

  type DatapointParser    = java.net.URI => Process[Task,Datapoint[Any]]
  type ClusterName        = String
  type ContinuousGauge[A] = Gauge[Continuous[A],A]

  val defaultWindow = 1 minute

  implicit def GroupMonoid[A](implicit G: Group[A]): Monoid[A] = new Monoid[A] {
    val zero = G.zero
    def append(a: A, b: => A) = G.plus(a, b)
  }

  implicit val dontUseTheDefaultStrategy: scalaz.concurrent.Strategy = null
  implicit val theDefaultStrategyCausesProblems: scalaz.concurrent.Strategy = null
}
