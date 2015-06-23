package funnel
package chemist

import org.scalacheck._
import Arbitrary._
import org.scalacheck.Prop.forAll
import scalaz.stream.Process
import scalaz.concurrent.Task
import scalaz.syntax.traverse._
import scalaz.std.vector._
import TargetLifecycle._
import java.net.URI

trait ArbitraryLifecycle {
  import shapeless.contrib.scalacheck._
  import TargetState._

  implicit val arbitraryURI: Arbitrary[URI] =
    Arbitrary(Gen.const(new URI("http://localhost/")))

  implicit val arbitraryState: Arbitrary[TargetState] =
    Arbitrary(Gen.oneOf(Unknown, Unmonitored, Assigned, Monitored, DoubleAssigned, DoubleMonitored, Fin))

  // Why does Shapeless not figure this out?
  implicit val target =
    Arbitrary(implicitly[Arbitrary[(ClusterName, URI, Boolean)]].arbitrary.map(p =>
      Target(p._1, p._2, p._3)))

  implicit val targetMessage: Arbitrary[TargetMessage] =
    Arbitrary(implicitly[Arbitrary[(Target, Long, String)]].arbitrary.flatMap { p =>
      val (target, time, id) = p
      val flask = FlaskID(id)
      Gen.oneOf(Gen.const(Discovery(target, time)),
                Gen.const(Assignment(target, flask, time)),
                Gen.const(Confirmation(target, flask, time)),
                Gen.const(Migration(target, flask, time)),
                Gen.const(Unassignment(target, flask, time)),
                Gen.const(Unmonitoring(target, flask, time)),
                Gen.alphaStr.map(Problem(target, flask, _, time)),
                Gen.const(Terminated(target, time)))
    })
}

object LifecycleSpec extends Properties("Lifecycle") with ArbitraryLifecycle {
  property("handle events") = forAll {(events: Vector[(TargetMessage, TargetState)]) =>
    val repo = new LoggingRepository
    val t = events.traverse_((TargetLifecycle.process(repo) _).tupled)
    (t as true).run
  }
}
