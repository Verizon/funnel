package funnel
package agent
package http

import org.scalatest._
import scalaz._

class JsonSpec extends FlatSpec with Matchers {
  import argonaut._, Argonaut._
  import JSON._
  import scala.concurrent.duration._

  implicit val instruments = new Instruments(1.minute)

  it should "decode json into an ArbitraryMetric" in {
    val in = """{"name":"ntp/whatever","kind":"counter","value": "123"}"""
    Parse.decodeEither[ArbitraryMetric](in) should equal (
      \/-(ArbitraryMetric("ntp/whatever", InstrumentKinds.Counter, Option("123"))))
  }

  it should "decode json into a InstrumentRequest" in {
    val in = """{"cluster":"foo-whatever","metrics":[{"name":"ntp/whatever","kind":"timer","value":"0.1234"}]}"""
    Parse.decodeOption[InstrumentRequest](in).get.timers.length should equal (1)
    // should be idempotent
    Parse.decodeOption[InstrumentRequest](in).get.timers.length should equal (1)
  }

}
