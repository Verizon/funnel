package oncue.svc.funnel.agent

import oncue.svc.funnel.zeromq._
import scalaz.stream.async.signal
import scalaz.stream.async.mutable.Signal
import scalaz.stream.Process
import scalaz.concurrent.Task

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