package funnel
package chemist

import journal.Logger
import org.scalatest.{FlatSpec, Matchers, BeforeAndAfterAll}
import funnel.Monitoring
import funnel.http.MonitoringServer
import scalaz.==>>
import scalaz.std.string._
import java.net.URI
import org.scalactic.TypeCheckedTripleEquals

class ShardingSpec extends FlatSpec with Matchers with TypeCheckedTripleEquals {

  import Sharding.Distribution

  implicit lazy val log: Logger = Logger("chemist-spec")

  implicit def tuple2target(in: (String,String)): Target =
    Target(in._1, new URI(in._2), false)

  def fakeFlask(id: String) = Flask(FlaskID(id), Location.localhost, Location.telemetryLocalhost)

  val d1: Distribution = ==>>(
    (fakeFlask("a").id, Set(("z","http://one.internal"))),
    (fakeFlask("d").id, Set(("y","http://two.internal"), ("w","http://three.internal"), ("v","http://four.internal"))),
    (fakeFlask("c").id, Set(("x","http://five.internal"))),
    (fakeFlask("b").id, Set(("z","http://two.internal"), ("u","http://six.internal")))
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
    Sharding.shards(d1).map(_.value) should equal (Seq("a", "c", "b", "d"))
  }

  it should "snapshot the exsiting shard distribution" in {
    Sharding.sorted(d1).map(_._1.value) should equal (Seq("a", "c", "b", "d"))
  }

  it should "correctly remove urls that are already being monitored" in {
    Sharding.deduplicate(i1)(d1) should equal ( Set[Target](
      ("v", "http://nine.internal"),
      ("u", "http://eight.internal"))
    )
  }

  it should "correctly calculate how the new request should be sharded over known flasks" in {
    val (s, newdist) = EvenSharding.distribution(i1)(d1)
    s.map {
      case (x,y) => x.value -> y
    }.toSet should === (Set(
                          "a" -> Target("u",new URI("http://eight.internal"), false),
                          "c" -> Target("v",new URI("http://nine.internal"), false)))

    val (s2,newdist2) = EvenSharding.distribution(i2)(d1)
    s2.map(_._2).toSet should === (Set(
                                     Target("v",new URI("http://omega.internal"), false),
                                     Target("w",new URI("http://alpha.internal"), false),
                                     Target("r",new URI("http://epsilon.internal"), false),
                                     Target("z",new URI("http://gamma.internal"), false),
                                     Target("u",new URI("http://beta.internal"), false),
                                     Target("z",new URI("http://omicron.internal"), false),
                                     Target("r",new URI("http://kappa.internal"), false),
                                     Target("r",new URI("http://theta.internal"), false),
                                     Target("p",new URI("http://zeta.internal"), false)))
  }

  it should "assign all the work when using random sharding" in {
    // this is a lame test, but because the assignment is random,
    // its not possible to write a test due to the non-deterministic nature
    val (s, newdist) = RandomSharding.distribution(i2)(d1)
    newdist.values.flatten.size should equal (i2.size)
  }

}
