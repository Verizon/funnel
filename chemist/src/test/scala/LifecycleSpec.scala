package oncue.svc.funnel.chemist

import org.scalatest.{FlatSpec,Matchers}
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.autoscaling.AmazonAutoScaling
import scalaz.{\/,\/-,-\/,==>>}
import scalaz.stream.{Process,Sink}
import scalaz.concurrent.Task
import Sharding.{Distribution,Target}

class LifecycleSpec extends FlatSpec with Matchers with ChemistSpec {
  val ec2 = TestAmazonEC2(Fixtures.instances)
  val uuid = java.util.UUID.randomUUID.toString
  val sqs1 = TestAmazonSQS(Fixtures.asgEvent(Launch, name = uuid))
  val sqs2 = TestAmazonSQS(Fixtures.asgEvent(Terminate, name = uuid))

  val asg1 = TestAmazonASG.single(_ => uuid)
  // val asg1 = TestAmazonASG.single(_ => "test-group")

  val r = new StatefulRepository(ec2)

  val k1 = "i-dx947af7"
  val k2 = "i-15807647"

  private def fromStream(sqs: AmazonSQS, asg: AmazonAutoScaling): Throwable \/ Action =
    Lifecycle.stream("name-of-queue")(r, sqs, asg
      ).until(Process.emit(false)).runLast.run.get // never do this anywhere but tests

  it should "side-effect and update the repository when a new flask is launched" in {
    fromStream(sqs1, asg1) should equal ( \/-(NoOp) )
  }

  it should "side-effect and update the repository when a flask is terminated" in {
    fromStream(sqs2, asg1) should equal ( \/-(NoOp) )
  }

}

//   val s: Sink[Task,Action] = Lifecycle.sink(r) //Process.emit { case x => Task.now( println(x) ) }

//   val f: (Set[Target], Repository) => Task[Unit] = (s,_) => Task.now(s.foreach(x => "  + $x"))


//   private def effect(a: Action, s: Sink[Task, Action]): Unit = {
//     val stream: Process[Task, Action] = Process.emit(a)
//     stream.evalMap(Lifecycle.transform(_,r)).to(s).run.run
//   }



//   ///// as we're testing effects in sinks, keep these in this order (urgh!) //////

//   it should "1. Lifecycle.toSink should compute and update state given 'AddCapacity' command" in {
//     effect(AddCapacity(k1), s)
//     r.assignedTargets(k1).run should equal (Set.empty[Target])

//     effect(AddCapacity(k2), s)
//     r.assignedTargets(k1).run should equal (Set.empty[Target])
//     r.assignedTargets(k2).run should equal (Set.empty[Target])
//   }

//   it should "2. Lifecycle.toSink should compute and update state given 'Redistribute' command" in {
//     val target = Target("foo", SafeURL("http://bar.internal"))
//     r.mergeDistribution(Distribution.empty.insert(k1, Set(target))).run

//     val stream: Process[Task, Action] = Process.emit(Redistribute(k1))
//     val x: Option[Action] = stream.evalMap(Lifecycle.transform(_,r)).runLast.run

//     x should equal ( Some(Redistributed(Map(Location(Some("foo.ext.amazonaws.com"), "",5775,"us-east-1b",false) -> List(target)))) )
//   }

// }
