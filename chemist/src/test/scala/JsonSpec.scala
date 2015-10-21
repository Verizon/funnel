package funnel
package chemist

import org.scalatest.{FlatSpec,Matchers}
import scalaz.\/
import java.net.URI

class JsonSpec extends FlatSpec with Matchers {
  import JSON._
  import argonaut._, Argonaut._

  it should "serilizes the clusters into JSON" in {
    val A1 = ("test", List(new URI("foo.tv"), new URI("bar.com")))
    A1.asJson.nospaces should equal ("""{"urls":["foo.tv","bar.com"],"cluster":"test"}""")

    val A2 = List(A1, A1)
    A2.asJson.nospaces should equal (
      """[{"urls":["foo.tv","bar.com"],"cluster":"test"},{"urls":["foo.tv","bar.com"],"cluster":"test"}]""")
  }
}
