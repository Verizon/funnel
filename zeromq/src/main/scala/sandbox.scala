package oncue.svc.funnel
package zeromq

import org.zeromq.ZMQ, ZMQ.Context, ZMQ.Socket
import scalaz.concurrent.Task
import java.util.concurrent.atomic.AtomicBoolean
import scalaz.stream.{Process,Cause}

object pub {

  def main(args: Array[String]): Unit = {

    import instruments._

    val E = Endpoint(mode = Publish, port = 7931)
    val M = Monitoring.default
    val T = counter("testing/foo")
    val close = new AtomicBoolean(false)
    val K = Process.repeatEval(Task.delay(close.get))

    // implicit class HaltWhenSyntax[O](p: Process[Task, O]){
    //   def haltWhen(bool: AtomicBoolean): Process[Task, O] =
    //     Process.repeatEval(Task.delay(bool.get)).zip(p).takeWhile(x => !x._1).map(_._2)
    // }

    val task = ZeroMQ.run(TCP,E)(M,K)

    println("Press [Enter] to stop the task")

    task.runAsync(_ => ())

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
    val context: Context = ZMQ.context(1)
    val socket: Socket = context.socket(ZMQ.SUB)
    val queue: Queue[String] = boundedQueue(100)
    val close = new AtomicBoolean(false)

    socket.connect("tcp://localhost:7931")
    socket.subscribe(Array.empty[Byte]) // providing an empty array means "all messages"

    Task {
      while(true){
        queue.enqueueOne(socket.recvStr).run // just throw away the result
      }
    }.runAsyncInterruptibly(_ => (), close)

    queue.dequeue.map(new String(_)).to(io.stdOut).run.runAsyncInterruptibly(_ => (), close)

    println("Press [Enter] to stop the task")

    Console.readLine() // block

    println("Stopping the task...")

    close.set(true) // stops the task.

    try {
      socket.close()
      context.close()
    } catch {
      case e: java.nio.channels.ClosedChannelException => ()
    }

  }
}