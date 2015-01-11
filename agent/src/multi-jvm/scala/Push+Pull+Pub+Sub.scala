package oncue.svc.funnel.agent

import oncue.svc.funnel._, zeromq._
import scalaz.concurrent.{Task,Strategy}
import java.util.concurrent.atomic.AtomicBoolean
import scalaz.stream.Process
import scala.concurrent.duration._

object TestingMultiJvmPusher1 extends ApplicationPusher("push-1")

object TestingMultiJvmPusher2 extends ApplicationPusher("push-2")

object TestingMultiJvmPublisher {
  def main(args: Array[String]): Unit = {
    val I = Endpoint(`Pull+Bind`, Address(IPC, host = "/tmp/feeds/0"))
    val O = Endpoint(Publish, Address(TCP, port = Option(7390)))
    new Proxy(I,O).task.run
  }
}

object TestingMultiJvmSubscriber {
  import scalaz.stream.io
  import scalaz.stream.async.signalOf

  def main(args: Array[String]): Unit = {
    val E = Endpoint(SubscribeAll, Address(TCP, port = Option(7390)))
    val S = signalOf[Boolean](true)

    Ø.link(E)(S)(Ø.receive).map(t => new String(t.bytes)).to(io.stdOut).run.runAsync(_ => ())

    Process.sleep(10.seconds)(Strategy.DefaultStrategy, Monitoring.schedulingPool)
      .onComplete(Process.eval_(Ø.monitoring.stop)).run.run

    println("Puller - Stopping the task...")
  }
}
