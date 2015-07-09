package funnel
package chemist
package aws

import org.scalatest.{FlatSpec, Matchers, BeforeAndAfterAll}
import scalaz._, Scalaz._
import LocationIntent._, NetworkScheme._
import zeromq.TCP

class DiscoverySpec extends FlatSpec with Matchers with BeforeAndAfterAll {
  val t1 = Some("http://@host:5555/stream/previous")
  val t2 = Some("http://@host:@port/stream/now?type='String'")
  val t3 = Some("zeromq+tcp://@host:7390/")

  val allTemplates = (t1 :: t2 :: t3 :: Nil).map(x => LocationTemplate(x.get))

  val D = new AwsDiscovery(null, null, null, allTemplates)

  private def make(template: Option[String], int: LocationIntent = Mirroring): String \/ Location =
    D.toLocation("foo.internal", "local", template, int)

  private def expct(
    port: Int,
    int: LocationIntent = Mirroring,
    sch: NetworkScheme = NetworkScheme.Http,
    tmpls: List[LocationTemplate] = allTemplates): String \/ Location =
    Location(
      host = "foo.internal",
      port = port,
      datacenter = "local",
      protocol = sch,
      isPrivateNetwork = true,
      intent = int,
      templates = tmpls).right

  it should s"prove '${t1.get}' can be turned into a Location" in {
    make(t1) should equal (expct(5555, tmpls = allTemplates.take(2)))
  }

  it should s"prove '${t3.get}' can be turned into a Location" in {
    make(t3, Supervision) should equal (
      expct(7390, Supervision, Zmtp(TCP), allTemplates.last :: Nil))
  }

  // this will fail because `toLocation` delibritly does not specifiy @port
  // and java.net.URI will get conffused about the @ in the string, so we
  // just fail fast here.
  it should s"prove '${t2.get}' cannot be turned into a location" in {
    make(t2).isLeft should equal (true)
  }

  it should "fail when no template is supplied" in {
    make(None).isLeft should equal (true)
  }

}
