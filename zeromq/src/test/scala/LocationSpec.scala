package oncue.svc.funnel
package zeromq

import org.scalatest.{FlatSpec,Matchers}
import java.net.URI
import scalaz.\/

class LocationSpec extends FlatSpec with Matchers {

  def lift(s: String): Option[Location] = Location(new URI(s)).toOption

  "location companion" should "parse from a URI" in {
    lift("ipc:///foo/bar.socket").map(_.hostOrPath
      ) should equal(Some("/foo/bar.socket"))

    lift("tcp://foo.com:7777").map(_.hostOrPath
      ) should equal(Some("foo.com:7777"))

    lift("foo:///tmp/bar.socket").map(_.hostOrPath
      ) should equal(None)

  }

}