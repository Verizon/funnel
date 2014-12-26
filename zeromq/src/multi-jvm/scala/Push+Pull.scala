package oncue.svc.funnel
package zeromq
package examples

import scalaz.concurrent.Task
import java.util.concurrent.atomic.AtomicBoolean
import scalaz.stream.Process
import scala.concurrent.duration._

object Fixtures {
  def data = large

  def makeBytes(max: Int): Array[Byte] =
    (0 to max).toSeq.map(_.toByte).toArray

  val tiny: Array[Byte] = makeBytes(2)
  val small: Array[Byte] = makeBytes(15)
  val medium: Array[Byte] = makeBytes(150)
  val large: Array[Byte] = makeBytes(1500)
  val megabitInBytes = 125000D
}

abstract class Pusher(name: String) {
  def main(args: Array[String]): Unit = {
    val E = Endpoint(`Push+Connect`, Address(IPC, host = "/tmp/feeds/0"))
    val close = new AtomicBoolean(false)
    val K = Process.repeatEval(Task.delay(close.get))

    println(s"$name - Press [Enter] to stop the task")

    val seq: Seq[Array[Byte]] = for(i <- 0 to 1000000) yield Fixtures.data
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
    val bytes = Fixtures.data.length * received.get
    val megabits = bytes.toDouble / Fixtures.megabitInBytes

    println("=================================================")
    println(s"duration  = $seconds seconds")
    println(s"msg/sec   = ${received.get.toDouble / seconds}")
    println(s"megabits  = $megabits")
    println(s"data mb/s = ${megabits.toDouble / seconds}")
    println("=================================================")


  }
}
