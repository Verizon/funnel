package intelmedia.ws.funnel
package elastic

import org.scalacheck._
import scalaz._
import Scalaz._

object ElasticTest extends Properties("elastic") {
  val genK = Gen.oneOf("k1", "k2")

  val genHost = Gen.oneOf("h1", "h2")

  val genName = for {
    k <- genK
    h <- genHost
  } yield s"$k:::$h"

  val key = genName map { k => Key(k, Units.Count: Units[Stats], "description") }

  val datapoint = for {
    k <- key
    d <- Gen.posNum[Double]
  } yield Datapoint(k, d)

  // At least one group per key/host pair. I.e. no data is lost.
  property("elasticGroupTop") = Prop.forAll(Gen.listOf(datapoint)) { dps =>
    val gs = Elastic.elasticGroup(dps ++ dps)
    val sz = gs.map(_.mapValues(_.size).values.sum).sum
    sz >= dps.size
  }

  // Emits as few times as possible
  property("elasticGroupBottom") = Prop.forAll(Gen.listOf(datapoint)) { dps =>
    val noDups = dps.groupBy(_.key).mapValues(_.head).values
    Elastic.elasticGroup(noDups ++ noDups).size == 1 || dps.size == 0
  }
}
