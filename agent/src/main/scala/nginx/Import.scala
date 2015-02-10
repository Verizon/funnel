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

  private[nginx] def statistics(from: URI): Task[Stats] =
    for {
      a <- Task(Source.fromURL(from.toURL).mkString)
      b <- Parser.parse(a).fold(e => Task.fail(e), Task.delay(_))
    } yield b

  def periodicly(from: URI)(frequency: Duration = 10.seconds): Process[Task,Stats] =
    Process.awakeEvery(frequency)(Strategy.Executor(defaultPool), schedulingPool
      ).evalMap(_ => statistics(from))
}
