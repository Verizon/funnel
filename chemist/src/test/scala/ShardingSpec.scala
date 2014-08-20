package oncue.svc.funnel.chemist

import org.scalatest.{FlatSpec, Matchers, BeforeAndAfterAll}
import intelmedia.ws.funnel.Monitoring
import intelmedia.ws.funnel.http.MonitoringServer
import oncue.svc.funnel.aws.{Group,Instance}
import scalaz.==>>
import java.net.URL
import Sharding.Distribution

class ShardingSpec extends FlatSpec with Matchers {


  val f1: Distribution = ==>>(
    ("a", Set(new URL("http://fuzz.com"))),
    ("d", Set(new URL("http://goo.com"), new URL("http://foo.com"), new URL("http://quz.com"))),
    ("c", Set(new URL("http://doo.com"))),
    ("b", Set(new URL("http://goo.com"), new URL("http://foo.com")))
  )

  it must "correctly sort the map and return the flasks in order of their set length" in {
    Sharding.flasks(f1) should equal (Set("a", "c", "b", "d"))
  }

  it must "snapshot the exsiting shard distribution" in {
    Sharding.sorted(f1).keySet should equal (Set("a", "c", "b", "d"))
  }

}