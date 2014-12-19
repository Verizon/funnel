package oncue.svc.funnel.agent

import oncue.svc.funnel._, zeromq._
import scalaz.concurrent.Task
import java.util.concurrent.atomic.AtomicBoolean
import scalaz.stream.Process
import scala.concurrent.duration._

abstract class Pusher(name: String, alive: Duration = 12.seconds) {
  def main(args: Array[String]): Unit = {
    import instruments._

    implicit val log: String => Unit = println _

    val E = Endpoint(`Push+Connect`, Address(IPC, host = "/tmp/feeds/0"))

    val M = Monitoring.default
    val T = counter("testing/foo")
    val close = new AtomicBoolean(false)
    val K = Process.repeatEval(Task.delay(close.get))

    println(s"$name - Press [Enter] to stop the task")

    Ø.link(E)(K)(s =>
      stream.from(M).through(Ø.write(s))).run.runAsync(_ => ())

    Thread.sleep(alive.toMillis) // block to keep the test JVM running

    println(s"$name - Stopping the task...")

    close.set(true) // stops the task.

    println(s"$name - Stopped (${close.get})")
  }
}

object SampleMultiJvmPusher1 extends Pusher("push-1")

object SampleMultiJvmPusher2 extends Pusher("push-2")

object SampleMultiJvmPublisher {
  def main(args: Array[String]): Unit = {
    val I = Endpoint(`Pull+Bind`, Address(IPC, host = "/tmp/feeds/0"))
    val O = Endpoint(Publish, Address(TCP, port = Option(7390)))
    new Agent(I,O).task.run
  }
}

object SampleMultiJvmSubscriber {
  import scalaz.stream.io

  def main(args: Array[String]): Unit = {
    val E = Endpoint(SubscribeAll, Address(TCP, port = Option(7390)))

    val close = new AtomicBoolean(false)
    val K = Process.repeatEval(Task.delay(close.get))

    Ø.link(E)(K)(Ø.receive).map(new String(_)).to(io.stdOut).run.runAsync(_ => ())

    Thread.sleep(10.seconds.toMillis) // block to keep the test JVM running

    println("Puller - Stopping the task...")

    close.set(true) // stops the task.

    println(s"Puller - Stopped (${close.get})")
  }
}


