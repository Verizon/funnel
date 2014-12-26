package oncue.svc.funnel
package zeromq
package examples

import scalaz.concurrent.Task
import java.util.concurrent.atomic.AtomicBoolean
import scalaz.stream.Process
import scala.concurrent.duration._

abstract class Pusher(name: String) {
  def main(args: Array[String]): Unit = {
    val E = Endpoint(`Push+Connect`, Address(IPC, host = "/tmp/feeds/0"))
    val close = new AtomicBoolean(false)
    val K = Process.repeatEval(Task.delay(close.get))

    println(s"$name - Press [Enter] to stop the task")

    val seq: Seq[Array[Byte]] = for(i <- 0 to 1000000) yield Array[Byte](1,2,3)
    // stupid scalac cant handle this done in-line.
    val proc: Process[Task, Array[Byte]] = Process.emitAll(seq)

    Ø.link(E)(K)(s =>
      proc.through(Ø.write(s))).run.runAsync(_ => ())

    println(s"$name - Stopping the task...")

    close.set(true) // stops the task.

    println(s"$name - Stopped (${close.get})")
  }
}

object PerfMultiJvmPusher1 extends Pusher("pusher-1")

object PerfMultiJvmPusher2 extends Pusher("pusher-2")

object PerfMultiJvmPusher3 extends Pusher("pusher-3")

object PerfMultiJvmPuller {
  import scalaz.stream.io
  import scalaz.stream.Channel
  import java.util.concurrent.atomic.AtomicLong

  val received = new AtomicLong(0L)

  def main(args: Array[String]): Unit = {
    val start = System.currentTimeMillis

    val E = Endpoint(`Pull+Bind`, Address(IPC, host = "/tmp/feeds/0"))

    val close = new AtomicBoolean(false)
    val K = Process.repeatEval(Task.delay(close.get))

    val printer: Channel[Task, String, Unit] = io.channel(
      _ => Task {
        val i = received.incrementAndGet
        if(i % 10000 == 0) println(i) // print it out every 1k increment
        else ()
      }
    )

    println("Press [Enter] to stop the task")

    Ø.link(E)(K)(Ø.receive)
      .map(new String(_))
      .through(printer)
      .run.runAsync(_ => ())

    while(received.get < 2999999){ }

    val finish = System.currentTimeMillis

    println("Puller - Stopping the task...")

    close.set(true) // stops the task.

    println(s"Puller - Stopped (${close.get})")

    val seconds = concurrent.duration.FiniteDuration(finish - start, "milliseconds").toSeconds

    println("=================================================")
    println(s"duration = $seconds seconds")
    println(s"msg/sec  = ${received.get.toDouble / seconds}")
    println(s"")
    println("=================================================")


  }
}
