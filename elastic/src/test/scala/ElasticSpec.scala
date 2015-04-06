package funnel
package elastic

import scala.concurrent.duration._
import org.scalatest._
import scalaz.concurrent.Strategy.Executor
import scalaz.concurrent.Task
import scalaz.stream._

class ElasticSpec extends FlatSpec with Matchers {
  val scheduler = Monitoring.schedulingPool
  val S = Executor(Monitoring.serverPool)

  val E = Elastic(Monitoring.default)

  import E._

  "elasticGroup" should "emit on timeout" in {
    val cfg = ElasticCfg("localhost", "index", "type", "dateFormat", List("k"), 5.seconds, 5000)
    val dp1 = Datapoint[Any](Key("k1", Units.Count: Units[Double], "description", Map(AttributeKeys.source -> "h1")), 3.14)
    val dp2 = Datapoint[Any](Key("k2", Units.Count: Units[Double], "description", Map(AttributeKeys.source -> "h2")), 2.17)
    val dps: Process[Task, Option[Datapoint[Any]]] = (Process(dp1) ++ Process(dp2)).map(Option.apply)
    val timeout = Process.sleep(5.seconds)(S, scheduler) ++
      Process(Option.empty[Datapoint[Any]]) ++
      Process.sleep(5.seconds)(S, scheduler) ++
      Process(Option.empty[Datapoint[Any]])
    val input = timeout.wye(dps ++ Process.sleep(15.seconds)(S, scheduler))(wye.merge).translate(lift)
    val ogs = input |> elasticGroup(List("k"))
    val result = ogs.runLast.run(cfg).run
    result should be ('defined)
    val gs = result.get
    gs.size shouldBe >= (dps.runLog.run.size)
  }
}

