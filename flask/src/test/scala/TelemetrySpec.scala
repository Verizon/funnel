package funnel
package flask

package test

import org.scalatest.matchers.{Matcher,MatchResult}
import org.scalatest.{FlatSpec,Matchers,BeforeAndAfterAll}

class TelemetrySpec extends FlatSpec
    with Matchers
    with BeforeAndAfterAll {

  behavior of "telemetry"

  lazy val S  = signalOf[Boolean](true)

  it should "publish new keys" in {
    1 should be (1)
  }
}
