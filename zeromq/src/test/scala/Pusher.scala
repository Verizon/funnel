package oncue.svc.funnel
package zeromq

import scalaz.concurrent.{Task,Strategy}
import scalaz.stream.Process

abstract class Pusher(name: String, fd: String, size: Int = 1000000) {
  def main(args: Array[String]): Unit = {
    Ø.log.info(s"Booting $name")

    val E = Endpoint(`Push+Connect`, Location(new java.net.URI(fd)))

    val seq: Seq[Array[Byte]] = for(i <- 0 to size) yield Fixtures.data
    // stupid scalac cant handle this in-line.
    val proc: Process[Task, Array[Byte]] = Process.emitAll(seq)

    Ø.link(E)(Ø.monitoring.alive)(socket =>
      proc.through(Ø.write(socket)
        ).onComplete(Process.eval(Ø.monitoring.stop))).run.run
  }
}

import scala.concurrent.duration._

abstract class ApplicationPusher(name: String, aliveFor: FiniteDuration = 12.seconds) {
  def main(args: Array[String]): Unit = {
    import instruments._

    implicit val log: String => Unit = println _

    val M = Monitoring.default
    val T = counter("testing/foo")

    T.incrementBy(2)

    println(s"$name - Press [Enter] to stop the task")

    Ø.monitoring.toUnixSocket(Settings.socket)

    Process.sleep(aliveFor)(Strategy.DefaultStrategy, Monitoring.schedulingPool)
      .onComplete(Process.eval_(Ø.monitoring.stop)).run.run

    Ø.log.info(s"Stopping the $name process...")
  }
}
