package oncue.svc.funnel
package zeromq

import org.zeromq.ZMQ, ZMQ.Context, ZMQ.Socket
import scalaz.concurrent.Task
import java.util.concurrent.atomic.AtomicBoolean
import scalaz.stream.{Process,Cause}

// object `sanbox-push` {
//   def main(args: Array[String]): Unit = {
//     import instruments._

//     val E = Endpoint(mode = Push, port = 7931)
//     val M = Monitoring.default
//     val T = counter("testing/foo")
//     val close = new AtomicBoolean(false)
//     val K = Process.repeatEval(Task.delay(close.get))

//     val task = ZeroMQ.run(TCP,E)(M,K)

//     println("Press [Enter] to stop the task")
//     task.runAsync(_ => ())
//     Console.readLine()
//     println("Stopping the task...")
//     close.set(true) // stops the task.
//   }
// }

// object `sandbox-pull` {
//   def main(args: Array[String]): Unit = {

//   }
// }


object pub {

  def main(args: Array[String]): Unit = {

    import instruments._

    implicit val log: String => Unit = println _

    val E = Endpoint(Publish, Address(TCP, port = 7931))

    val M = Monitoring.default
    val T = counter("testing/foo")
    val close = new AtomicBoolean(false)
    val K = Process.repeatEval(Task.delay(close.get))

    // implicit class HaltWhenSyntax[O](p: Process[Task, O]){
    //   def haltWhen(bool: AtomicBoolean): Process[Task, O] =
    //     Process.repeatEval(Task.delay(bool.get)).zip(p).takeWhile(x => !x._1).map(_._2)
    // }

    val proc: Process[Task, Boolean] = ZeroMQ.fooo(E)(K)(
      s => stream.from(M).through(ZeroMQ.channel(s)))

    // val task = ZeroMQ.outbound(E)(stream.from(M),K,ZeroMQ.channel) //ZeroMQ.run(TCP,E)(M,K)

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

    ZeroMQ.fooo(E)(K)(ZeroMQ.consume).to(io.stdOut).run.runAsync(_ => ())

    Console.readLine() // block

    println("Stopping the task...")

    close.set(true) // stops the task.

    println(s"Stopped (${close.get})")
  }
}