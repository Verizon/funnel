package funnel
package chemist

import org.scalacheck._
import Arbitrary._
import org.scalacheck.Prop.forAll
import shapeless.contrib.scalacheck._
import scalaz.stream.Process
import scalaz.concurrent.Task
import TargetLifecycle._

trait ArbitraryLifecycle {
  import TargetState._

  implicit val arbitraryState: Arbitrary[TargetState] = Arbitrary(Gen.oneOf(Unknown, Unmonitored, Assigned, Monitored, DoubleAssigned, DoubleMonitored, Fin))
}

object LifecycleSpec extends Properties("Lifecycle") with ArbitraryLifecycle {

  property("handle events") = forAll {(events: Seq[(TargetMessage, TargetState)]) =>
    (Process.eval(Task.now(events)).flatMap(Process.emitAll) pipe TargetLifecycle.filter).run.map(_ => true).run
  }
}
