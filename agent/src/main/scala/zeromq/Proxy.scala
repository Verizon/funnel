package funnel
package agent
package zeromq

import funnel.zeromq._
import scalaz.stream.async.signal
import scalaz.stream.async.mutable.Signal
import scalaz.stream.Process
import scalaz.concurrent.Task

class Proxy(I: Endpoint, O: Endpoint){
  private val alive: Signal[Boolean] = signal[Boolean]
  private val stream: Process[Task,Boolean] =
    Ø.link(O)(alive)(s =>
      Ø.link(I)(alive)(Ø.receive
        ).through(Ø.writeTrans(s)))

  def task: Task[Unit] =
    for {
      _ <- alive.set(true)
      _ <- stream.run
    } yield ()
}
