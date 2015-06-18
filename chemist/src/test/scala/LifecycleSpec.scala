package funnel
package chemist

import org.scalacheck._
import Arbitrary._
import org.scalacheck.Prop.forAll
import shapeless.contrib.scalacheck._
import scalaz.stream.Process
import scalaz.concurrent.Task
import scalaz.syntax.traverse._
import scalaz.std.vector._
import TargetLifecycle._
import java.net.URI

trait ArbitraryLifecycle {
  import TargetState._

  implicit val arbitraryURI: Arbitrary[URI] =
    Arbitrary(Gen.const(new URI("http://localhost/")))

  implicit val arbitraryState:
    Arbitrary[TargetState] = Arbitrary(Gen.oneOf(Unknown, Unmonitored, Assigned, Monitored, DoubleAssigned, DoubleMonitored, Fin))
}

object LifecycleSpec extends Properties("Lifecycle") with ArbitraryLifecycle {
  property("handle events") = forAll {(events: Vector[(TargetMessage, TargetState)]) =>
    val repo = new LoggingRepository
    val t = events.traverse_((TargetLifecycle.process(repo) _).tupled)
    (t as true).run
  }
}
