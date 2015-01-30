package oncue.svc.funnel
package zeromq

import oncue.svc.funnel._, zeromq._
import scalaz.concurrent.{Task,Strategy}
import java.util.concurrent.atomic.AtomicBoolean
import scalaz.stream.Process
import scala.concurrent.duration._
import java.net.URI

object ExampleMultiJvmPusher1 extends ApplicationPusher("push-1")

object ExampleMultiJvmPuller {
  import scalaz.stream.io
  import scalaz.stream.Channel
  import java.util.concurrent.atomic.AtomicLong
  import sockets._

  val received = new AtomicLong(0L)

  def main(args: Array[String]): Unit = {
    val start = System.currentTimeMillis

    val E = Endpoint(pull &&& bind, Location(Settings.uri))

    Ø.link(E)(Fixtures.signal)(Ø.receive)
      .map(_.toString)
      .to(io.stdOut)
      .run.runAsync(_ => ())

    Thread.sleep(10.seconds.toMillis)

    stop(Fixtures.signal).run

    Ø.log.info("Stopping the pulling process...")
  }
}

