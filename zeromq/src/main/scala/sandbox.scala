package oncue.svc.funnel
package zeromq

import org.zeromq.ZMQ, ZMQ.Context, ZMQ.Socket
import scalaz.concurrent.Task
import java.util.concurrent.atomic.AtomicBoolean
import scalaz.stream.Process

object pub {

  def main(args: Array[String]): Unit = {

    import instruments._

    val P = Publisher(port = 7931)
    val M = Monitoring.default
    val T = counter("testing/foo")
    val close = new AtomicBoolean(false)

    val task: Task[Unit] = for {
      x    <- P.setup(threadCount = 1)
      (a,b) = x
      _    <- P.publish(M,a)(d => println(d)).flatMap { b =>
                if(close.get) Process.halt
                else Process.emit(b)
              }.run
      _    <- P.destroy(a,b)
    } yield ()

    task.runAsyncInterruptibly(_ => (), close)

    println("Press [Enter] to stop the task")

    Console.readLine()

    println("Stopping the task...")

    close.set(true) // stops the task.

    // P.destroy()
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