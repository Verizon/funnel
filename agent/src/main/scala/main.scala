package oncue.svc.funnel.agent

import oncue.svc.funnel.zeromq._
import journal.Logger
import scalaz.stream.async.signal
import scalaz.stream.async.mutable.Signal
import scalaz.stream.Process
import scalaz.concurrent.Task
import journal.Logger

class Proxy(I: Endpoint, O: Endpoint){
  private val alive: Signal[Boolean] = signal[Boolean]
  private val stream: Process[Task,Boolean] =
    Ø.link(O)(alive.continuous)(s =>
      Ø.link(I)(alive.continuous)(Ø.receive
        ).map(_.bytes).through(Ø.write(s)))

  def task: Task[Unit] =
    for {
      _ <- alive.set(false)
      _ <- stream.run
    } yield ()
}

object Main {
  private val log = Logger[Main.type]

  def main(args: Array[String]): Unit = {
    val I = Endpoint(`Pull+Bind`, Address(IPC, host = "/tmp/feeds/0"))
    val O = Endpoint(Publish, Address(TCP, port = Option(7390)))

    new Proxy(I,O).task.runAsync(_.fold(
      e => log.error(s"0mq proxy resulted in failure: $e"),
      _ => ()
    ))
  }
}
