package funnel
package elastic

import scala.concurrent.duration._
import org.scalacheck.{Properties => P, _}
import scalaz.stream._

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
