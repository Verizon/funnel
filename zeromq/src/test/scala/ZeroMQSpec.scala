package oncue.svc.funnel
package zeromq

import org.scalatest._
import scalaz.stream.{Process,Channel,io}
import scalaz.concurrent.Task
import java.util.concurrent.atomic.{AtomicBoolean,AtomicLong}

// class ZeroMQSpec extends FlatSpec with Matchers {
//   val E1 = Endpoint(`Push+Connect`, Address(IPC, host = "/tmp/feeds/0"))

//   it should "foo" in {
//     val task = for {
//       _ <- Ø.foo(E1, Ø.stream.alive).run
//     } yield ()

//     task.run
//   }
// }



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
