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
    val alive = signalOf[Boolean](true)

    val out: Process[Task, Array[Byte]] =
      Process.emitAll((1 to 100).map(_ => Fixtures.small))

    Ø.link(E2)(alive)(Ø.receive).map(t => new String(t.bytes)).to(io.stdOut).run.run

    Ø.link(E1)(alive)(s => out.through(Ø.write(s))).run.run


    // alive.set(false).run
    // alive.close.run
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
