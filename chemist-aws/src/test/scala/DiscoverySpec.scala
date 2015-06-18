package funnel
package chemist
package aws

import org.scalatest.{FlatSpec, Matchers, BeforeAndAfterAll}
import scalaz._, Scalaz._
import LocationIntent._

class DiscoverySpec extends FlatSpec with Matchers with BeforeAndAfterAll {
  val t1 = Some("http://@host:5555/stream/previous")
  val t2 = Some("http://@host:@port/stream/now?type='String'")
  val allTemplates = (t1 :: t2 :: Nil).map(x => LocationTemplate(x.get))

  val D = new AwsDiscovery(null, null, allTemplates)

  private def make(template: Option[String]): String \/ Location =
    D.toLocation("foo.internal", "local", template, Mirroring)

  private def expct(host: String, port: Int): String \/ Location =
    Location(
      host = host,
      port = port,
      datacenter = "local",
      protocol = NetworkScheme.Http,
      isPrivateNetwork = true,
      intent = Mirroring,
      templates = allTemplates).right

  it should s"prove '$t1' can be turned into a Location" in {
    make(t1) should equal (expct("foo.internal", 5555))
  }
  // this will fail because `toLocation` delibritly does not specifiy @port
  // and java.net.URI will get conffused about the @ in the string, so we
  // just fail fast here.
  it should s"prove '$t2' cannot be turned into a location" in {
    make(t2).isLeft should equal (true)
  }

  it should "fail when no template is supplied" in {
    make(None).isLeft should equal (true)
  }

}
