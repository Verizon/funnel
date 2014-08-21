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

  val d1: Distribution = ==>>(
    ("a", Set(("z","http://one.com"))),
    ("d", Set(("y","http://two.com"), ("w","http://three.com"), ("v","http://four.com"))),
    ("c", Set(("x","http://five.com"))),
    ("b", Set(("z","http://two.com"), ("u","http://six.com")))
  )

  it must "correctly sort the map and return the flasks in order of their set length" in {
    Sharding.flasks(d1) should equal (Set("a", "c", "b", "d"))
  }

  it must "snapshot the exsiting shard distribution" in {
    Sharding.sorted(d1).keySet should equal (Set("a", "c", "b", "d"))
  }

  it must "correctly remove urls that are already being monitored" in {
    val s: Set[Target] = Set(
      ("w", "http://three.com"),
      ("u", "http://eight.com"),
      ("v", "http://nine.com"),
      ("z", "http://two.com")
    )

    Sharding.deduplicate(s)(d1) should equal ( Set[Target](
      ("v", "http://nine.com"),
      ("u", "http://eight.com"))
    )
  }

}