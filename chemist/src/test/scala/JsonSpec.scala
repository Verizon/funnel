package funnel
package chemist

import org.scalatest.{FlatSpec,Matchers}
import scalaz.\/
import java.net.URI

class JsonSpec extends FlatSpec with Matchers {
  import JSON._
  import argonaut._, Argonaut._
  import TargetLifecycle._, TargetState._

  it should "serilizes the clusters into JSON" in {
    val A1 = ("test", List(new URI("foo.tv"), new URI("bar.com")))
    A1.asJson.nospaces should equal ("""{"urls":["foo.tv","bar.com"],"cluster":"test"}""")

    val A2 = List(A1, A1)
    A2.asJson.nospaces should equal (
      """[{"urls":["foo.tv","bar.com"],"cluster":"test"},{"urls":["foo.tv","bar.com"],"cluster":"test"}]""")
  }

  it should "serilize a pair of (uri, statechange) into JSON" in {
    val t1 = Target("testcluster", new URI("http://xxxx:5775/stream/previous"), true)
    val c1 = RepoEvent.StateChange(Unmonitored, Assigned, Discovery(t1, 123456l))
    (c1 :: Nil).asJson.nospaces should fullyMatch regex (
      """\[\{"to-state":"Assigned","from-state":"Unmonitored","message":\{"time":"[^"]*","target":"http://xxxx:5775/stream/previous","type":"Discovery"\}\}\]"""
    )
  }

}
