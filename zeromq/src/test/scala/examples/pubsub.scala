package oncue.svc.funnel
package zeromq

import org.zeromq.ZMQ, ZMQ.Context, ZMQ.Socket
import scalaz.concurrent.Task
import java.util.concurrent.atomic.AtomicBoolean
import scalaz.stream.{Process,Cause}

object pub {

  def main(args: Array[String]): Unit = {

    import instruments._

    implicit val log: String => Unit = println _

    val E = Endpoint(Publish, Address(TCP, port = 7931))

    val M = Monitoring.default
    val T = counter("testing/foo")
    val close = new AtomicBoolean(false)
    val K = Process.repeatEval(Task.delay(close.get))

    val proc: Process[Task, Boolean] = Ø.link(E)(K)(
      s => stream.from(M).through(Ø.channel(s)))

    println("Press [Enter] to stop the task")

    proc.run.runAsync(_ => ())

    Console.readLine()

    println("Stopping the task...")

    close.set(true) // stops the task.

    println(s"Stopped (${close.get})")
  }
}

object sub {

  import scalaz.stream.async.boundedQueue
  import scalaz.stream.async.mutable.Queue
  import scalaz.stream.io

  def main(args: Array[String]): Unit = {
    val E = Endpoint(mode = SubscribeAll, Address(TCP, port =7931))
    val close = new AtomicBoolean(false)
    val K = Process.repeatEval(Task.delay(close.get))

    println("Press [Enter] to stop the task")

    Ø.link(E)(K)(Ø.consume).to(io.stdOut).run.runAsync(_ => ())

    Console.readLine() // block

    println("Stopping the task...")

    close.set(true) // stops the task.

    println(s"Stopped (${close.get})")
  }
}