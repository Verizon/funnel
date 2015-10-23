package funnel
package chemist

import org.scalatest.{FlatSpec,Matchers}
import scala.concurrent.duration._
import scalaz.stream.Process
import java.net.URI

class PipelineSpec extends FlatSpec with Matchers {
  import PlatformEvent._
  import Chemist.{Flow,Context}
  import Sharding.Distribution
  import Pipeline.{contextualise,partition}
  import Fixtures._

  implicit class AsTarget(s: String){
    def target: Target =
      Target(java.util.UUID.randomUUID.toString, new URI(s))
  }

  implicit class AsNewTargetFlow(targets: List[Target]){
    def flow: Flow[PlatformEvent] =
      Process.emitAll(targets).map(t => Context(d1, NewTarget(t)))
  }

  val d1 = Distribution.empty
    .insert(flask01, Set.empty)
    .insert(flask02, Set.empty)

  it should "correctly distribute the work to one of the flasks" in {
    val p1: Flow[PlatformEvent] =
      List("http://localhost:8888/stream/previous".target).flow

    (p1.map(partition(TestDiscovery, RandomSharding)).runLast.run
      .get.value match {
        case Distribute(d) => d.values.flatMap(identity).length
        case _ => 0
      }) should equal(1)
  }

  // this is a little lame
  it should "produce a plan for every input target" in {
    val p2 = List(
      "http://localhost:4001/stream/previous".target,
      "http://localhost:4001/stream/now?type=%22String%22".target,
      "http://localhost:4002/stream/previous".target,
      "http://localhost:4002/stream/now?type=%22String%22".target,
      "http://localhost:4003/stream/previous".target,
      "http://localhost:4003/stream/now?type=%22String%22".target,
      "http://localhost:4004/stream/previous".target,
      "http://localhost:4004/stream/now?type=%22String%22".target
    )

    val accum: List[Plan] =
      p2.flow.map(partition(TestDiscovery, RandomSharding))
      .scan(List.empty[Plan])((a,b) => a :+ b.value)
      .runLast.run
      .toList
      .flatten
    accum.length should equal (p2.length)
  }
}