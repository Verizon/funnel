package oncue.svc.funnel.chemist

import org.scalatest.{FlatSpec,Matchers}
import scalaz.{\/,\/-,-\/,==>>}
import scalaz.stream.{Process,Sink}
import scalaz.concurrent.Task
import Sharding.{Distribution,Target}

// class LifecycleSpec extends FlatSpec with Matchers with ChemistSpec {
//   val sqs = new TestAmazonSQS
//   val ec2 = TestAmazonEC2(Fixtures.instances)
//   val r = new StatefulRepository(ec2)
//   val s: Sink[Task,Action] = Lifecycle.sink(r) //Process.emit { case x => Task.now( println(x) ) }
//   val k1 = "i-dx947af7"
//   val k2 = "i-15807647"
//   val f: (Set[Target], Repository) => Task[Unit] = (s,_) => Task.now(s.foreach(x => "  + $x"))


//   private def effect(a: Action, s: Sink[Task, Action]): Unit = {
//     val stream: Process[Task, Action] = Process.emit(a)
//     stream.evalMap(Lifecycle.transform(_,r)).to(s).run.run
//   }

//   it should "Lifecycle.stream should process the ASG event JSON into the right algebra" in {
//     Lifecycle.stream("doesntexist")(sqs)
//       .until(Process.emit(false)).runLast.run should equal (
//         Some(\/-(Redistribute("i-dd947af7"))))
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
