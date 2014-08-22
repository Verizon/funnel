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
    Sharding.flasks(d1) should equal (Set("a", "c", "b", "d"))
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
      (Target("u",SafeURL("http://eight.internal")),"a") ::
      (Target("v",SafeURL("http://nine.internal")),"c") :: Nil
    )

    Sharding.calculate(i2)(d1) should equal (
      (Target("v",SafeURL("http://omega.internal")),"a") ::
      (Target("w",SafeURL("http://alpha.internal")),"c") ::
      (Target("r",SafeURL("http://epsilon.internal")),"b") ::
      (Target("z",SafeURL("http://gamma.internal")),"d") ::
      (Target("u",SafeURL("http://beta.internal")),"a") ::
      (Target("z",SafeURL("http://omicron.internal")),"c") ::
      (Target("r",SafeURL("http://kappa.internal")),"b") ::
      (Target("r",SafeURL("http://theta.internal")),"d") ::
      (Target("p",SafeURL("http://zeta.internal")),"a") :: Nil
    )
  }

}