package funnel
package chemist
package aws

import org.scalatest.{FlatSpec, Matchers, BeforeAndAfterAll}
import scalaz._, Scalaz._

class DiscoverySpec extends FlatSpec with Matchers with BeforeAndAfterAll {
  val allTemplates = Seq(
      "http://@host:5555/stream/previous",
      "http://@host:@port/stream/now?type='String'"
    ).map(LocationTemplate)

  val D = new AwsDiscovery(null, null, allTemplates)

  private def make(template: String): String \/ Location =
    D.toLocation(
      Fixtures.instances.head,
      "funnel:mirror:uri-template",
      LocationIntent.Mirroring)(Map("funnel:mirror:uri-template" -> template)
    )

  private def expct(host: String, port: Int): String \/ Location =
    Location(
      host = host,
      port = port,
      datacenter = "us-east-1b",
      protocol = NetworkScheme.Http,
      isPrivateNetwork = true,
      intent = LocationIntent.Mirroring,
      templates = allTemplates).right

  it should s"prove '${allTemplates.head.template}' can be turned into a Location" in {
    make(allTemplates.head.template) should equal (
      expct("foo.internal", 5555))
  }
  // this will fail because `toLocation` delibritly does not specifiy @port
  // and java.net.URI will get conffused about the @ in the string, so we
  // just fail fast here.
  it should s"prove '${allTemplates(1)}' cannot be turned into a location" in {
    make(allTemplates(1).template).isLeft should equal (true)
  }

}
