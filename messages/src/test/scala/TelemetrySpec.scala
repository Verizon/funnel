package funnel
package messages

import org.scalacheck._
import org.scalacheck.Prop.forAll
import shapeless.contrib.scalacheck._
import scalaz.{-\/,\/,\/-}
import Telemetry._
import zeromq._

object TelemetrySpec extends Properties("Telemetry codecs") with ArbitraryTelemetry {

  property("scodec key roundtrip") = forAll {(k: Key[Any]) ⇒
    keyEncode.encode(k).fold(_ => false,
                             bits => keyDecode.decodeValidValue(bits) == k)
  }


}
