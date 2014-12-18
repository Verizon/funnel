package oncue.svc.funnel
package zeromq
package examples

import scalaz.concurrent.Task
import java.util.concurrent.atomic.AtomicBoolean
import scalaz.stream.Process
import scala.concurrent.duration._

abstract class Pusher(name: String, alive: Duration = 15.seconds) {
  def main(args: Array[String]): Unit = {
    import instruments._

    implicit val log: String => Unit = println _

    // val E = Endpoint(Publish, Address(TCP, port = 7931))
    val E = Endpoint(`Push+Connect`, Address(IPC, host = "/tmp/feeds/0"))

    val M = Monitoring.default
    val T = counter("testing/foo")
    val close = new AtomicBoolean(false)
    val K = Process.repeatEval(Task.delay(close.get))

    val proc: Process[Task, Boolean] = Ø.link(E)(K)(
      s => stream.from(M).through(Ø.channel(s)))

    println(s"$name - Press [Enter] to stop the task")

    proc.run.runAsync(_ => ())

    Thread.sleep(alive.toMillis) // block to keep the test JVM running

    println(s"$name - Stopping the task...")

    close.set(true) // stops the task.

    println(s"$name - Stopped (${close.get})")
  }
}

object SampleMultiJvmPusher1 extends Pusher("pub1")

object SampleMultiJvmPusher2 extends Pusher("pub2")

object SampleMultiJvmPuller {
  import scalaz.stream.io

  def main(args: Array[String]): Unit = {
    val E = Endpoint(`Pull+Bind`, Address(IPC, host = "/tmp/feeds/0"))

    val close = new AtomicBoolean(false)
    val K = Process.repeatEval(Task.delay(close.get))

    println("Press [Enter] to stop the task")

    Ø.link(E)(K)(Ø.consume).to(io.stdOut).run.runAsync(_ => ())

    Thread.sleep(12.seconds.toMillis) // block to keep the test JVM running

    println("Stopping the task...")

    close.set(true) // stops the task.

    println(s"Stopped (${close.get})")
  }
}
