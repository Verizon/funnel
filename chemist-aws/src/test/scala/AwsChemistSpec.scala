package funnel
package chemist
package aws

import org.scalatest.{FlatSpec, Matchers, BeforeAndAfterAll}
import scala.concurrent.duration._
import scalaz.NonEmptyList
import scalaz.stream.async.signalOf
import scalaz.stream.async.mutable.Signal

class ChemistAwsSpec extends FlatSpec with Matchers {

  val c1 = AwsConfig(
    templates = Nil,
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

  "findInstances" should "honour the private network config" in {
    val c = new AwsChemist
    val p1 = new Aws { val config = c1 }
    val p2 = new Aws { val config = c2 }
    c.filterTargets(targets).run(p1).run.length should equal (3)
    c.filterTargets(targets).run(p2).run.length should equal (2)
  }
}
