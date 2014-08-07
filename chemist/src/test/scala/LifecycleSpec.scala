package oncue.svc.funnel.chemist

import org.scalatest.{FlatSpec,Matchers}
import scalaz.{\/,\/-,-\/}
import scalaz.stream.Process
import scalaz.concurrent.Task

class LifecycleSpec extends FlatSpec with Matchers {
  val sqs = new TestAmazonSQS

  it must "Lifecycle.stream should process the ASG event JSON into the right algebra" in {
    Lifecycle.stream("doesntexist")(sqs)
      .until(Process.emit(false)).runLast.run should equal (Some(\/-(Redistribute("i-dd947af7"))))
  }
}
