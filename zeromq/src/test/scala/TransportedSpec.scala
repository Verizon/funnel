package funnel
package zeromq

import org.scalacheck._
import Prop._
import Arbitrary._

object TransportedSpec extends Properties("Transported") {
  implicit val arbitraryScheme = Arbitrary(Gen.oneOf(Schemes.fsm,Schemes.telemetry/*,Schemes.unknown*/))
  implicit val arbitraryVersion = Arbitrary(Gen.oneOf(Versions.v1, Versions.v2/*, Versions.unknown*/))
  implicit val arbitraryWindow = Arbitrary(Gen.oneOf(Windows.now, Windows.sliding, Windows.previous/*, Windows.unknown*/))
  implicit val arbitraryTopic = Arbitrary(arbitrary[String].map(Topic(_)))
  implicit val arbitraryTransported = Arbitrary(for {
    ser <- arbitrary[Serial]
    sch <- arbitrary[Scheme]
    ver <- arbitraryVersion.arbitrary
    win <- arbitrary[Option[Window]]
    top <- arbitrary[Option[Topic]]
    byt <- arbitrary[Array[Byte]]
  } yield Transported(ser, sch, ver, win, top, byt))

  property("roundtrip")  = forAll { (t: Transported) =>
    val rt = Transported(t.header, t.serial, t.bytes)
    t == rt
  }
}