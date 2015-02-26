package funnel
package zeromq

import org.scalacheck._
import Prop._
import Arbitrary._
import shapeless.contrib.scalacheck._

object TransportedSpec extends Properties("Transported") {
  implicit val arbitraryScheme = Arbitrary(Gen.oneOf(Schemes.fsm,Schemes.telemetry,Schemes.unknown))
  implicit val arbitraryVersion = Arbitrary(Gen.oneOf(Versions.v1, Versions.v2, Versions.unknown))
  implicit val arbitraryTopic = Arbitrary(arbitrary[String].map(Topic(_)))
  property("roundtrip")  = forAll { (t: Transported) =>
    val rt = Transported(t.header, t.bytes)
    t == rt
  }
}
