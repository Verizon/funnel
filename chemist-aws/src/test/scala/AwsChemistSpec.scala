package funnel
package chemist
package aws

import scala.language.postfixOps
import org.scalatest.{FlatSpec, Matchers, BeforeAndAfterAll}
import org.scalatest.OptionValues._
import java.net.URI
import scala.concurrent.duration._
import scalaz.NonEmptyList
import scalaz.stream.async.signalOf
import scalaz.stream.async.mutable.Signal
import unfiltered.scalatest.netty.Served
import chemist.JSON._
import aws.JSON._
import argonaut._, Argonaut._

class ChemistAwsSpec extends FlatSpec with Matchers with Served {

  implicit def URIDecodeJson: DecodeJson[URI] = DecodeJson(_.as[String].map(new URI(_)))

  val c1 = AwsConfig(
    templates = Nil,
    network = NetworkConfig("localhost",12345),
    queue = QueueConfig("test","test"),
    sns = null,
    sqs = null,
    ec2 = null,
    asg = null,
    commandTimeout = 2.seconds,
    includeVpcTargets = true,
    sharder = RandomSharding
  )

  val c2 = c1.copy(includeVpcTargets = false)

  def instance(isPrivate: Boolean, name: String): AwsInstance = {
    val l = Location(
      host = "127.0.0.1",
      port = 45698,
      datacenter = "dc",
      isPrivateNetwork = isPrivate,
      intent = LocationIntent.Mirroring,
      templates = Fixtures.defaultTemplates
    )
    AwsInstance(
      id = name,
      tags = Map("type" -> name, "revision" -> "1.2.3"),
      locations = NonEmptyList(l)
    )
  }

  val f1 = instance(false, "foo") ::
           instance(false, "bar") ::
           instance(true, "qux") :: Nil

  val targets = f1.map(i => TargetID(i.id) -> i.targets)

  def setup = { s =>
    val c = new AwsChemist[DefaultAws]
    val p = new DefaultAws { val config = c2 }
    val server = new Server(c, p)
    s.plan(server)
  }

  "findInstances" should "honour the private network config" in {
    val c = new AwsChemist[DefaultAws]
    val p1 = new DefaultAws { val config = c1 }
    val p2 = new DefaultAws { val config = c2 }
    c.filterTargets(targets).run(p1).run._1.length should equal (3)
    val r1 = c.filterTargets(targets).run(p2).run
    r1._1.length should equal (2)
    r1._2.length should equal (1)
  }

  "GET /unmonitorable" should "return discovered unmonitorable targets" in {
    val js = http(host / "unmonitorable" as_str)
    val uris = Parse.decodeOption[List[URI]](js)
    uris.value should be (List(new URI("http://127.0.0.1:45698")))
  }
}
