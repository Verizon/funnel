package funnel
package chemist

import zeromq.{TCP,UDP}
import org.scalatest.{FlatSpec, Matchers}

class NetworkSchemeSpec extends FlatSpec with Matchers {
  import NetworkScheme._

  it should "correctly parse the http scheme" in {
    NetworkScheme.fromString("http") should equal (Some(Http))
  }

  it should "correctly parse the zeromq+tcp scheme" in {
    NetworkScheme.fromString("zeromq+tcp") should equal (Some(Zmtp(TCP)))
  }

  it should "correctly parse the zeromq+udp scheme" in {
    NetworkScheme.fromString("zeromq+udp") should equal (Some(Zmtp(UDP)))
  }
}
