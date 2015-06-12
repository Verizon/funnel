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

  def instance(isPrivate: Boolean, name: String): AwsInstance = {
    val l = Location("127.0.0.1", 45698, "dc", isPrivateNetwork = isPrivate)
    AwsInstance(id = name, location = l, tags = Map("type" -> name, "revision" -> "1.2.3"))
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
