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

  val d1 = Distribution.empty
    .insert(flask01, Set.empty)
    .insert(flask02, Set.empty)

  val p1: Flow[PlatformEvent] =
    Process.emit("http://localhost:8888/stream/previous".target
      ).map(t => Context(d1, NewTarget(t)))

  it should "correctly distribute the work to one of the flasks" in {
    (p1.map(partition(TestDiscovery, RandomSharding)).runLast.run
      .get.value match {
        case Distribute(d) => d.values.flatMap(identity).length
        case _ => 0
      }) should equal(1)
  }
}