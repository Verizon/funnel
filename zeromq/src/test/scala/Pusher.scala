package funnel
package zeromq

import scalaz.concurrent.{Task,Strategy}
import scalaz.stream.async.signalOf
import scalaz.stream.Process
import java.net.URI

abstract class Pusher(name: String, uri: URI = Settings.uri, size: Int = 1000000) {
  def main(args: Array[String]): Unit = {
    import sockets._

    Ø.log.info(s"Booting $name...")

    val E = Endpoint.unsafeApply(push &&& connect, uri)

    implicit val batransport: Transportable[Array[Byte]] = Transportable { ba =>
      Transported(Schemes.unknown, Versions.unknown, None, ba)
    }

    val seq: Seq[Array[Byte]] = for(i <- 0 to size) yield Fixtures.data
    // stupid scalac cant handle this in-line.
    val proc: Process[Task, Array[Byte]] = Process.emitAll(seq)

    Ø.link(E)(Fixtures.signal)(socket =>
      proc.through(Ø.write(socket)
        ).onComplete(Process.eval(stop(Fixtures.signal)))).run.run
  }
}

import scala.concurrent.duration._

abstract class ApplicationPusher(name: String, aliveFor: FiniteDuration = 12.seconds) {
  def main(args: Array[String]): Unit = {
    import instruments._

    Ø.log.info(s"Booting $name...")

    implicit val log: String => Unit = println _

    val M = Monitoring.default
    val T = counter("testing/foo")

    T.incrementBy(2)

    Publish.toUnixSocket(Settings.uri.toString, Fixtures.signal)

    Process.sleep(aliveFor)(Strategy.DefaultStrategy, Monitoring.schedulingPool)
      .onComplete(Process.eval_(Fixtures.signal.get)).run.run

    Ø.log.info(s"Stopping the '$name' process...")
  }
}
