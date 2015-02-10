package oncue.svc.funnel
package agent
package nginx

import java.net.URI
import scala.io.Source
import scalaz.stream.Process
import scalaz.concurrent.{Task,Strategy}
import scala.concurrent.duration._

object Import {
  import Monitoring.{defaultPool,schedulingPool}

  object metrics {
    import instruments._

    val Connections = gauge("nginx/connections", 0d, Units.Count, description = "Number of active connections")
    val Reading     = gauge("nginx/reading", 0d, Units.Count, description = "Number of active connections reading from the network")
    val Writing     = gauge("nginx/writing", 0d, Units.Count, description = "Number of active connections writing from the network")
    val Waiting     = gauge("nginx/waiting", 0d, Units.Count, description = "Number of active connections waiting to to be serviced")
    val Accepts     = gauge("nginx/lifetime/accepts", 0d, Units.Count, description = "Number of accepted requests this server has seen since bootup")
    val Handled     = gauge("nginx/lifetime/handled", 0d, Units.Count, description = "Number of handled requests this server has seen since bootup")
    val Requests    = gauge("nginx/lifetime/requests", 0d, Units.Count,  description = "Number of recieved requests this server has seen since bootup")
  }

  private[nginx] def statistics(from: URI): Task[Stats] =
    for {
      a <- Task(Source.fromURL(from.toURL).mkString)
      b <- Parser.parse(a).fold(e => Task.fail(e), Task.delay(_))
    } yield b

  private[nginx] def updateMetrics(s: Stats): Unit = {
    metrics.Connections.set(s.connections)
    metrics.Reading.set(s.reading)
    metrics.Writing.set(s.writing)
    metrics.Waiting.set(s.waiting)
    metrics.Accepts.set(s.accepts)
    metrics.Handled.set(s.handled)
    metrics.Requests.set(s.requests)
  }

  def periodicly(from: URI)(frequency: Duration = 10.seconds): Process[Task,Unit] =
    Process.awakeEvery(frequency)(Strategy.Executor(defaultPool), schedulingPool
      ).evalMap(_ => statistics(from).map(updateMetrics))
}
