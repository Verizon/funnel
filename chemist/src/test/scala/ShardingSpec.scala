package oncue.svc.funnel.chemist

import org.scalatest.{FlatSpec, Matchers, BeforeAndAfterAll}
import intelmedia.ws.funnel.Monitoring
import intelmedia.ws.funnel.http.MonitoringServer
import oncue.svc.funnel.aws.{Group,Instance}
import scalaz.==>>

class ShardingSpec extends FlatSpec with Matchers {

  import Sharding.{Distribution,Target}

  implicit def tuple2target(in: (String,String)): Target =
    Target(in._1, SafeURL(in._2))

  // val d1: Distribution = ==>>(
  //   ("a", Set(("http://fuzz.com"))),
  //   ("d", Set(("http://goo.com"), ("http://foo.com"), ("http://quz.com"))),
  //   ("c", Set(("http://doo.com"))),
  //   ("b", Set(("http://goo.com"), ("http://foo.com")))
  // )

  // it must "correctly sort the map and return the flasks in order of their set length" in {
  //   Sharding.flasks(d1) should equal (Set("a", "c", "b", "d"))
  // }

  // it must "snapshot the exsiting shard distribution" in {
  //   Sharding.sorted(d1).keySet should equal (Set("a", "c", "b", "d"))
  // }

  it must "correctly remove urls that are already being monitored" in {
    val s: Set[Target] = Set(
      ("test", "http://fuzz.com"),
      ("test", "http://bar2.com"),
      ("fuuu", "http://eeee.com"),
      ("ggg", "http://goo.com"),
      ("ggg", "http://wwww.com"),
      ("ggg", "http://rrrr.com")
    )

    Sharding.deduplicate(s)(d1) should equal ( Set[Target](
      ("x", "http://eeee.com"),
      ("x", "http://rrrr.com"),
      ("x", "http://wwww.com"),
      ("x", "http://bar2.com"))
    )
  }

}