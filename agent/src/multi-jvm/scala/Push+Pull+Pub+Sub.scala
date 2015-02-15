package funnel
package agent

import oncue.svc.funnel.zeromq._, sockets._
import scalaz.concurrent.{Task,Strategy}
import java.util.concurrent.atomic.AtomicBoolean
import scalaz.stream.Process
import scala.concurrent.duration._

object TestingMultiJvmPusher1 extends ApplicationPusher("push-1")

object TestingMultiJvmPusher2 extends ApplicationPusher("push-2")

object TestingMultiJvmPublisher {
  def main(args: Array[String]): Unit = {

    val (i,o) = (for {
      a <- Endpoint(pull &&& bind, Settings.uri)
      b <- Endpoint(publish &&& bind, Settings.tcp)
    } yield (a,b)).getOrElse(sys.error("Unable to configure the endpoints for the agent."))

    new zeromq.Proxy(i,o).task.run
  }
}

object TestingMultiJvmSubscriber {
  import scalaz.stream.io
  import scalaz.stream.async.signalOf

  def main(args: Array[String]): Unit = {
    val E = Endpoint(subscribe &&& (connect ~ topics.all), Settings.tcp
      ).getOrElse(sys.error("Unable to configure the TCP subscriber endpoint"))

    Ø.link(E)(Fixtures.signal)(Ø.receive).map(t => new String(t.bytes)).to(io.stdOut).run.runAsync(_ => ())

    Process.sleep(10.seconds)(Strategy.DefaultStrategy, Monitoring.schedulingPool)
      .onComplete(Process.eval_(Fixtures.signal.get)).run.run

    println("Subscriber - Stopping the task...")
  }
}
