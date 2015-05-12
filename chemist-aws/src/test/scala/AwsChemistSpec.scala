package funnel
package chemist
package aws

import org.scalatest.{FlatSpec, Matchers, BeforeAndAfterAll}
import scala.concurrent.duration._
import scalaz.stream.async.signalOf
import scalaz.stream.async.mutable.Signal

class ChemistAwsSpec extends FlatSpec with Matchers {

  val c1 = AwsConfig(
    resources = Nil,
    network = NetworkConfig("localhost",12345),
    queue = QueueConfig("test","test"),
    sns = null,
    sqs = null,
    ec2 = null,
    asg = null,
    commandTimeout = 2.seconds,
    includeVpcTargets = true
  )

  val c2 = c1.copy(includeVpcTargets = false)

  def instance(isPrivate: Boolean, name: String): Instance = {
    val l = Location("127.0.0.1", 45698, "dc", isPrivateNetwork = isPrivate)
    AwsInstance(id = "x", location = l, firewalls = Nil, tags = Map("type" -> name, "revision" -> "1.2.3"))
  }

  val f1 = instance(true, "flask") ::
           instance(false, "foo") ::
           instance(false, "bar") ::
           instance(true, "qux") :: Nil

  "findInstances" should "not include flasks and honour the private network config" in {
    AwsChemist.filterInstances(f1)(c1).length should equal (3)
    AwsChemist.filterInstances(f1)(c2).length should equal (2)
  }
}
