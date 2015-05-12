package funnel
package chemist
package aws

import org.scalatest.{FlatSpec,Matchers}
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.autoscaling.AmazonAutoScaling
import scalaz.{\/,\/-,-\/,==>>}
import scalaz.stream.{Process,Sink}
import scalaz.concurrent.Task
import scalaz.stream.async.signalOf
import scalaz.stream.async.mutable.Signal
import scalaz.std.string._
import Sharding.Distribution

class LifecycleSpec extends FlatSpec with Matchers {
  import PlatformEvent._

  val ec2 = TestAmazonEC2(Fixtures.instances)
  val uuid = java.util.UUID.randomUUID.toString
  val sqs1 = TestAmazonSQS(Fixtures.asgEvent(Launch, name = uuid, instanceId = "i-flaskAAA"))
  val sqs2 = TestAmazonSQS(Fixtures.asgEvent(Terminate, name = uuid, instanceId = "i-flaskAAA"))
  val sqs3 = TestAmazonSQS("invalid-message")
  val resources = List("stream/previous")

  val asg1 = TestAmazonASG.single(_ => uuid)
  // val asg1 = TestAmazonASG.single(_ => "test-group")

  val dsc = new AwsDiscovery(ec2, asg1)

  val k1 = "i-dx947af7"
  val k2 = "i-15807647"


  val signal: Signal[Boolean] = signalOf(true)

  private def fromStream(sqs: AmazonSQS, asg: AmazonAutoScaling): Throwable \/ Seq[PlatformEvent] =
    Lifecycle.stream("name-of-queue", "stream/previous" :: Nil, signal)(sqs, asg, ec2, dsc
      ).until(Process.emit(false)).runLast.run.get // never do this anywhere but tests

  behavior of "Lifecycle.stream"

  it should "side-effect and update the repository when a new flask is launched" in {
    val \/-(Seq(NewFlask(f))) = fromStream(sqs1, asg1)
    f.id.value should equal( "i-flaskAAA")
  }

  it should "side-effect and update the repository when a flask is terminated" in {
    fromStream(sqs2, asg1) should equal ( \/-(Seq(TerminatedFlask(FlaskID("i-flaskAAA"))))) // empty because there is no work
  }

  it should "produce a parse exception in the event the message on SQS cannot be parsed" in {
    fromStream(sqs3, asg1).swap.toOption.get shouldBe a [MessageParseException]
  }

  behavior of "Lifecycle.interpreter"

  import scalaz.syntax.traverse._
  import scalaz.{Unapply,Traverse}
  import scalaz.syntax.either._

  def check(json: String): Task[Throwable \/ Seq[PlatformEvent]] =
    Lifecycle.parseMessage(TestMessage(json)
      ).traverse(Lifecycle.interpreter(_, resources, signal)(asg1, ec2, dsc))


  it should "parse messages and produce the right action" in {
    val \/-(Seq(NewFlask(f))) = check(Fixtures.asgEvent(Launch, instanceId = "i-flaskAAA")).run
    f.id.value should equal("i-flaskAAA")
    check("INVALID-MESSAGE").map(_ => true).run should equal (true)
  }

  // TODO: finish refactoring this.
  // it should "produce a Redistributed when given a flask launch task" in {
  //   Lifecycle.interpreter(AutoScalingEvent("i-xxx", Launch), Nil
  //     )(r, asg1, ec2, dsc).run should equal (true)
  // }

  // it should "Lifecycle.toSink should compute and update state given 'AddCapacity' command" in {
  //   effect(AddCapacity(k1), s)
  //   r.assignedTargets(k1).run should equal (Set.empty[Target])

  //   effect(AddCapacity(k2), s)
  //   r.assignedTargets(k1).run should equal (Set.empty[Target])
  //   r.assignedTargets(k2).run should equal (Set.empty[Target])
  // }


  // it should "2. Lifecycle.toSink should compute and update state given 'Redistribute' command" in {
  //   val target = Target("foo", SafeURL("http://bar.internal"))
  //   r.mergeDistribution(Distribution.empty.insert(k1, Set(target))).run

  //   val stream: Process[Task, Action] = Process.emit(Redistribute(k1))
  //   val x: Option[Action] = stream.evalMap(Lifecycle.transform(_,r)).runLast.run

  //   x should equal ( Some(Redistributed(Map(Location(Some("foo.ext.amazonaws.com"), "",5775,"us-east-1b",false) -> List(target)))) )
  // }

}
