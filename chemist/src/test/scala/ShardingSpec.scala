package oncue.svc.laboratory

import org.scalatest.{FlatSpec, Matchers, BeforeAndAfterAll}
import oncue.svc.funnel.Monitoring
import oncue.svc.funnel.http.MonitoringServer
import scalaz.==>>
import scalaz.std.string._

class ShardingSpec extends FlatSpec with Matchers with ChemistSpec  {

  import Sharding.{Distribution,Target}

  implicit def tuple2target(in: (String,String)): Target =
    Target(in._1, SafeURL(in._2))

  val d1: Distribution = ==>>(
    ("a", Set(("z","http://one.internal"))),
    ("d", Set(("y","http://two.internal"), ("w","http://three.internal"), ("v","http://four.internal"))),
    ("c", Set(("x","http://five.internal"))),
    ("b", Set(("z","http://two.internal"), ("u","http://six.internal")))
  )

  val i1: Set[Target] = Set(
    ("w", "http://three.internal"),
    ("u", "http://eight.internal"),
    ("v", "http://nine.internal"),
    ("z", "http://two.internal")
  )

  val i2: Set[Target] = Set(
    ("w", "http://alpha.internal"),
    ("u", "http://beta.internal"),
    ("v", "http://omega.internal"),
    ("z", "http://gamma.internal"),
    ("p", "http://zeta.internal"),
    ("r", "http://epsilon.internal"),
    ("r", "http://theta.internal"),
    ("r", "http://kappa.internal"),
    ("z", "http://omicron.internal")
  )

  it should "correctly sort the map and return the flasks in order of their set length" in {
    Sharding.shards(d1) should equal (Set("a", "c", "b", "d"))
  }

  it should "snapshot the exsiting shard distribution" in {
    Sharding.sorted(d1).keySet should equal (Set("a", "c", "b", "d"))
  }

  it should "correctly remove urls that are already being monitored" in {
    Sharding.deduplicate(i1)(d1) should equal ( Set[Target](
      ("v", "http://nine.internal"),
      ("u", "http://eight.internal"))
    )
  }

  it should "correctly calculate how the new request should be sharded over known flasks" in {
    Sharding.calculate(i1)(d1) should equal (
      ("a", Target("u",SafeURL("http://eight.internal"))) ::
      ("c", Target("v",SafeURL("http://nine.internal"))) :: Nil
    )

    Sharding.calculate(i2)(d1) should equal (
      ("a", Target("v",SafeURL("http://omega.internal"))) ::
      ("c", Target("w",SafeURL("http://alpha.internal"))) ::
      ("b", Target("r",SafeURL("http://epsilon.internal"))) ::
      ("d", Target("z",SafeURL("http://gamma.internal"))) ::
      ("a", Target("u",SafeURL("http://beta.internal"))) ::
      ("c", Target("z",SafeURL("http://omicron.internal"))) ::
      ("b", Target("r",SafeURL("http://kappa.internal"))) ::
      ("d", Target("r",SafeURL("http://theta.internal"))) ::
      ("a", Target("p",SafeURL("http://zeta.internal"))) :: Nil
    )
  }

}
