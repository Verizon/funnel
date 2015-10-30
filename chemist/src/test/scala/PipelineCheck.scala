package funnel
package chemist

import java.net.URI
import java.util.UUID

import scalaz.==>>

import LocationIntent._
import Sharding.Distribution

import org.scalacheck.{ Arbitrary, Gen, Properties }
import Arbitrary.arbitrary
import Gen.{ alphaNumChar, listOf, listOfN, oneOf }
import org.scalacheck.Prop.{ BooleanOperators, forAll }

object PipelineSpecification extends Properties("Pipeline") {
  import Fixtures._

  property("newFlask works") = forAll { (f: Flask, d: Distribution) =>
    val (nd, _) = Pipeline.handle.newFlask(f, RandomSharding)(d)
    ("The existing Distribution does not contain the Flask" |:
      !d.keySet.contains(f)) &&
    ("The new Distribution contains the Flask" |:
      nd.keySet.contains(f)) &&
    ("The existing and new Distributions have the same Targets" |:
      Sharding.targets(d) == Sharding.targets(nd))
  }

  property("newTarget works") = forAll { (t: Target, d: Distribution) =>
    val nd = Pipeline.handle.newTarget(t, RandomSharding)(d)
    ("The existing Distribution does not contain the Target" |:
      !Sharding.targets(d).contains(t)) &&
    ("The new Distribution contains the Target" |:
      (d.size > 0) ==> Sharding.targets(nd).contains(t))
  }
}
