package funnel
package chemist

import java.net.URI
import java.util.UUID
import scalaz.==>>
import LocationIntent._
import Sharding.Distribution
import org.scalacheck.{Properties,Gen}
import org.scalacheck.Prop.{ BooleanOperators, forAll }

object ShardingProp extends Properties("sharding") {
  import Fixtures._

  val genNonEmptyTargetSet = Gen.nonEmptyContainerOf[Set,Target](genTarget)

  property("work assignment") = forAll(
    genNonEmptyTargetSet,
    genDistribution,
    genSharder
  ){ (w: Set[Target], d: Distribution, shd: Sharder) =>
      val (s, newdist) = RandomSharding.distribution(w)(d)
      ("assign all the given targets if the distribution has flasks" |:
        (if(!d.hasFlasks){ newdist.values.flatten.size == 0 }
        else { newdist.values.flatten.size == (d.values.flatten.size + w.size) }))
    }

  property("deduplication") = forAll(
    genNonEmptyTargetSet,
    genDistribution
  ){ (w: Set[Target], d: Distribution) =>
    val work = w ++ d.elemAt(0).map { case (k,v) => v }.getOrElse(Set.empty)
    ("should remove existing targets" |: (Sharding.deduplicate(work)(d) == w))
  }

}
