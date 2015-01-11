package oncue.svc.funnel
package zeromq

import scalaz.concurrent.Task
import scalaz.stream.Process

abstract class Pusher(name: String, fd: String, size: Int = 1000000) {
  def main(args: Array[String]): Unit = {
    Ø.log.info(s"Booting $name")

    val E = Endpoint(`Push+Connect`, Address(IPC, host = fd))

    val seq: Seq[Array[Byte]] = for(i <- 0 to size) yield Fixtures.data
    // stupid scalac cant handle this in-line.
    val proc: Process[Task, Array[Byte]] = Process.emitAll(seq)

    Ø.link(E)(Ø.monitoring.alive)(socket =>
      proc.through(Ø.write(socket)
        ).onComplete(Process.eval(Ø.monitoring.stop))).run.run
  }
}
