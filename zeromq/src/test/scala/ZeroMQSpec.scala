package oncue.svc.funnel
package zeromq

import org.scalatest._
import scalaz.stream.{Process,Channel,io}
import scalaz.concurrent.Task
// import java.util.concurrent.atomic.{AtomicBoolean,AtomicLong}
import java.net.URI
import scalaz.stream.async.signalOf

class ZeroMQSpec extends FlatSpec with Matchers {
  val L = Location(new URI("ipc:///tmp/funneltest.socket"))
  val E1 = Endpoint(`Push+Connect`, L)
  val E2 = Endpoint(`Pull+Bind`, L)

  it should "foo" in {
    // val S = signalOf[Boolean](false)

    val kill: Process[Task,Boolean] = Process.emitAll(Seq(true,true,true))

    val out: Process[Task, Array[Byte]] =
      Process.emitAll(Seq("foo","bar","baz").map(_.getBytes))

    val sockout: Process[Task, Boolean] =
      Ø.linkP(E1)(kill)(s => out.through(Ø.write(s)))

    val sockin: Process[Task,Transported] =
      Ø.linkP(E2)(kill)(Ø.receive)

    println(">>><<<<>>>>>")

    println(sockout.run.run)
    println(sockin.run.run)
    println(">>><<<<>>>>>")

    // val task: Task[Unit] = for {
    //   _ <- Ø.link(E1)(S)(s => out.through(Ø.write(s))).run
    //   x <- Ø.link(E2)(S)(Ø.receive).run
    //   _ <- S.set(false)
    // } yield x

    // task.run
  }
}

// class ZeroMQSpec extends FlatSpec with Matchers with BeforeAndAfterAll {
//   val push  = Endpoint(`Push+Connect`, Address(IPC, host = "/tmp/feeds/0"))
//   val pull  = Endpoint(`Pull+Bind`, Address(IPC, host = "/tmp/feeds/0"))
//   val close = new AtomicBoolean(false)
//   val received = new AtomicLong(0L)
//   val kill  = Process.repeatEval(Task.delay(close.get))
//   val data: Seq[Array[Byte]] = for(i <- 0 to 10) yield Fixtures.data
//   val counter: Channel[Task, String, Unit] = io.channel(
//     _ => Task {
//       val i = received.incrementAndGet
//       if(i == 10){
//         println("Closing the stream")
//         close.set(true)
//       } else println("Count: " + i)
//     }
//   )

//   it should "be able to send and recieve messages" in {
//     Ø.link(pull)(kill)(Ø.receive)
//       .map(_.toString)
//       .through(counter)
//       .run.runAsync(_ => ())

//     val proc: Process[Task, Array[Byte]] = Process.emitAll(data)
//     Ø.link(push)(kill)(s => proc.through(Ø.write(s))).run.runAsync(_ => ())

//     received.get should equal (10)
//   }
// }
