package funnel
package chemist

import org.scalatest.{FlatSpec,Matchers}
import scalaz.\/

class JsonSpec extends FlatSpec with Matchers {
  import JSON._
  import argonaut._, Argonaut._

  it should "serilizes the buckets into JSON" in {
    val A1 = ("test", List(SafeURL("foo.tv"), SafeURL("bar.com")))
    A1.asJson.nospaces should equal ("""{"urls":["foo.tv","bar.com"],"bucket":"test"}""")

    val A2 = List(A1, A1)
    A2.asJson.nospaces should equal (
      """[{"urls":["foo.tv","bar.com"],"bucket":"test"},{"urls":["foo.tv","bar.com"],"bucket":"test"}]""")
  }
}
