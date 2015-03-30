package funnel
package elastic

import scala.concurrent.duration._
import org.scalacheck.{Properties => P, _}
import org.scalatest._
import scalaz.stream._
import scalaz.concurrent.Strategy.DefaultStrategy
import scalaz.concurrent.Task

class ElasticSpec extends FlatSpec with Matchers {
  implicit val scheduler = Monitoring.schedulingPool

  val E = Elastic(Monitoring.default)

  import E._

  "elasticGroup" should "emit on timeout" in {
    val cfg = ElasticCfg("localhost", "index", "type", "dateFormat", List("k"), 5.seconds, 5000)
    val dp1 = Datapoint[Any](Key("k1", Units.Count: Units[Double], "description", Map(AttributeKeys.source -> "h1")), 3.14)
    val dp2 = Datapoint[Any](Key("k2", Units.Count: Units[Double], "description", Map(AttributeKeys.source -> "h2")), 2.17)
    val dps: Process[Task, Option[Datapoint[Any]]] = (Process(dp1) ++ Process(dp2)).map(Option.apply)
    val timeout = Process.sleep(5.seconds) ++
      Process(Option.empty[Datapoint[Any]]) ++
      Process.sleep(5.seconds) ++
      Process(Option.empty[Datapoint[Any]])
    val input = timeout.wye(dps ++ Process.sleep(15.seconds))(wye.merge).translate(lift)
    val ogs = input |> elasticGroup(List("k"))
    val result = ogs.runLast.run(cfg).run
    result should be ('defined)
    val gs = result.get
    gs.size shouldBe >= (dps.runLog.run.size)
  }
}

object ElasticTest extends P("elastic") {
  val genName = Gen.oneOf("k1", "k2")

  val genHost = Gen.oneOf("h1", "h2")

  val genKey = for {
   n <- genName
   h <- genHost
  } yield Key(n, Units.Count: Units[Double], "description", Map(AttributeKeys.source -> h))

  val datapoint = for {
    k <- genKey
    d <- Gen.posNum[Double]
  } yield Option(Datapoint(k, d))

  val E = Elastic(Monitoring.default)

  import E._

  // At least one group per key/host pair. I.e. no data is lost.
  property("elasticGroupTop") = Prop.forAll(Gen.listOf(datapoint)) { dps =>
    val gs = elasticGroup(List("k"))(dps ++ dps)
    val sz = gs.map(_.mapValues(_.size).values.sum).sum
    sz >= dps.size
  }

  // Emits as few times as possible
  property("elasticGroupBottom") = Prop.forAll(Gen.listOf(datapoint)) { dps =>
    val noDups = dps.groupBy(_.get.key).mapValues(_.head).values
    elasticGroup(List("k"))(noDups ++ noDups).size == 1 || dps.size == 0
  }

  property("elasticUngroup") = Prop.forAll(Gen.listOf(datapoint)) { dps =>
    val gs = elasticGroup(List("k"))(dps ++ dps)
    val ug = elasticUngroup("flask")(gs)
    ug.forall(_.fold(!_.fields.isEmpty, _.isObject))
  }
}
