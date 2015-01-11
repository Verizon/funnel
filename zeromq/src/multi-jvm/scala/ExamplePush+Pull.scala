package oncue.svc.funnel
package zeromq

import oncue.svc.funnel._, zeromq._
import scalaz.concurrent.{Task,Strategy}
import java.util.concurrent.atomic.AtomicBoolean
import scalaz.stream.Process
import scala.concurrent.duration._

object ExampleSettings {
  val socket = "/tmp/funnel-example"
}

abstract class ExamplePusher(name: String, aliveFor: FiniteDuration = 12.seconds) {
  def main(args: Array[String]): Unit = {
    import instruments._

    implicit val log: String => Unit = println _

    val M = Monitoring.default
    val T = counter("testing/foo")

    T.incrementBy(2)

    println(s"$name - Press [Enter] to stop the task")

    Ø.monitoring.toUnixSocket(ExampleSettings.socket)

    Process.sleep(aliveFor)(Strategy.DefaultStrategy, Monitoring.schedulingPool)
      .onComplete(Process.eval_(Ø.monitoring.stop)).run.run

    Ø.log.info(s"Stopping the $name process...")
  }
}

object ExampleMultiJvmPusher1 extends ExamplePusher("push-1")

object ExampleMultiJvmPuller {
  import scalaz.stream.io
  import scalaz.stream.Channel
  import java.util.concurrent.atomic.AtomicLong

  val received = new AtomicLong(0L)

  def main(args: Array[String]): Unit = {
    val start = System.currentTimeMillis

    val E = Endpoint(`Pull+Bind`, Address(IPC, host = ExampleSettings.socket))

    Ø.link(E)(Ø.monitoring.alive)(Ø.receive)
      .map(_.toString)
      .to(io.stdOut)
      .run.runAsync(_ => ())

    Thread.sleep(10.seconds.toMillis)

    Ø.monitoring.stop.run

    Ø.log.info("Stopping the pulling process...")
  }
}

