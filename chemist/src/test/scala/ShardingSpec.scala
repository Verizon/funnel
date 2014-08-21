package oncue.svc.funnel.chemist

import org.scalatest.{FlatSpec, Matchers, BeforeAndAfterAll}
import intelmedia.ws.funnel.Monitoring
import intelmedia.ws.funnel.http.MonitoringServer
import oncue.svc.funnel.aws.{Group,Instance}
import scalaz.==>>
// import java.net.URL
import Sharding.Distribution

class ShardingSpec extends FlatSpec with Matchers {


  val d1: Distribution = ==>>(
    ("a", Set(SafeURL("http://fuzz.com"))),
    ("d", Set(SafeURL("http://goo.com"), SafeURL("http://foo.com"), SafeURL("http://quz.com"))),
    ("c", Set(SafeURL("http://doo.com"))),
    ("b", Set(SafeURL("http://goo.com"), SafeURL("http://foo.com")))
  )

  it must "correctly sort the map and return the flasks in order of their set length" in {
    Sharding.flasks(d1) should equal (Set("a", "c", "b", "d"))
  }

  it must "snapshot the exsiting shard distribution" in {
    Sharding.sorted(d1).keySet should equal (Set("a", "c", "b", "d"))
  }

  it must "foo" in {
    val s = Set(
      ("test", SafeURL("http://fuzz.com")),
      ("test", SafeURL("http://bar2.com")),
      ("fuuu", SafeURL("http://eeee.com")),
      ("ggg", SafeURL("http://goo.com")),
      ("ggg", SafeURL("http://wwww.com")),
      ("ggg", SafeURL("http://rrrr.com"))
    )

    println {
      Sharding.deduplicate(s)(d1)
    }


  }

}