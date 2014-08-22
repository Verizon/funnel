package oncue.svc.funnel.chemist

import org.scalatest.{FlatSpec,Matchers}
import scalaz.{\/,\/-,-\/,==>>}
import scalaz.stream.{Process,Sink}
import scalaz.concurrent.Task
import Sharding.Distribution

class LifecycleSpec extends FlatSpec with Matchers {
  val sqs = new TestAmazonSQS
  val r1: Ref[Distribution] = new Ref(==>>())
  val k1 = "i-dd947af7"

  private def effect[A](a: A, s: Sink[Task, Action]): Unit = {
    val stream: Process[Task, Action] = Process.emit(AddCapacity(k1))
    stream.to(s).run.run
  }

  it should "Lifecycle.stream should process the ASG event JSON into the right algebra" in {
    Lifecycle.stream("doesntexist")(sqs)
      .until(Process.emit(false)).runLast.run should equal (
        Some(\/-(Redistribute("i-dd947af7"))))
  }

  it should "Lifecycle.toSink should compute and update state given 'AddCapacity' command" in {
    effect(AddCapacity(k1), Lifecycle.toSink(r1))
    r1.get should equal ( ==>>(k1 -> Set.empty) )
  }

}
