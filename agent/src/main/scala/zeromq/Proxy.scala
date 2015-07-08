package funnel
package agent
package zeromq

import funnel.zeromq._
import scalaz.stream.async.signalOf
import scalaz.stream.async.mutable.Signal
import scalaz.stream.Process
import scalaz.concurrent.Task

class Proxy(I: Endpoint, O: Endpoint){
  private val alive: Signal[Boolean] = signalOf[Boolean](true)
  private val stream: Process[Task,Boolean] =
    Ø.link(O)(alive)(s =>
      Ø.link(I)(alive)(Ø.receive
        ).through(Ø.writeTrans(s)))

  def task: Task[Unit] = stream.run
}
