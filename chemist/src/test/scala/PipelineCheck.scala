//: ----------------------------------------------------------------------------
//: Copyright (C) 2015 Verizon.  All Rights Reserved.
//:
//:   Licensed under the Apache License, Version 2.0 (the "License");
//:   you may not use this file except in compliance with the License.
//:   You may obtain a copy of the License at
//:
//:       http://www.apache.org/licenses/LICENSE-2.0
//:
//:   Unless required by applicable law or agreed to in writing, software
//:   distributed under the License is distributed on an "AS IS" BASIS,
//:   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//:   See the License for the specific language governing permissions and
//:   limitations under the License.
//:
//: ----------------------------------------------------------------------------
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
