package oncue.svc.funnel
package agent
package nginx

import scalaz.\/
import java.net.{URI,URL}
import scala.io.Source
import scalaz.stream.Process
import scalaz.concurrent.{Task,Strategy}
import scala.concurrent.duration._

object Import {
  import Monitoring.{serverPool,schedulingPool}

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

  private[this] def fetch(url: URL): Throwable \/ String =
    \/.fromTryCatchNonFatal(Source.fromInputStream(url.openConnection.getInputStream).mkString)

  def statistics(from: URI): Task[Option[Stats]] =
    Task {
      fetch(from.toURL)
        .flatMap(Parser.parse)
        .fold(e => Option.empty, w => Option(w))
    }(serverPool)

  private[nginx] def updateMetrics(stats: Option[Stats]): Unit = {
    stats.foreach { s =>
      metrics.Connections.set(s.connections)
      metrics.Reading.set(s.reading)
      metrics.Writing.set(s.writing)
      metrics.Waiting.set(s.waiting)
      metrics.Accepts.set(s.accepts)
      metrics.Handled.set(s.handled)
      metrics.Requests.set(s.requests)
    }
  }

  def periodicly(from: URI)(frequency: Duration = 10.seconds, log: journal.Logger): Process[Task,Unit] =
    Process.awakeEvery(frequency)(Strategy.Executor(serverPool), schedulingPool
      ).evalMap(_ => statistics(from).handleWith {
        case e: java.io.FileNotFoundException =>
          log.error(s"An error occoured with the nginx import from $from")
          e.printStackTrace
          Task.now(Option.empty[Stats])
      }.map(updateMetrics))
}
