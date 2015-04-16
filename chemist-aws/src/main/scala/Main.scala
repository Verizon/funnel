package funnel
package chemist
package aws

import scalaz.stream.async.mutable.Signal
import scalaz.concurrent.Task
import scalaz.syntax.monad._
import http.MonitoringServer

object Main {
  def main(args: Array[String]): Unit = {

    val signal: Signal[Boolean] = scalaz.stream.async.signalOf(true)

    val chemist = new AwsChemist

    val aws = new Aws {
      val config = (for {
        a <- defaultKnobs
        b <- knobs.aws.config
      } yield Config.readConfig(a ++ b, signal)).run
    }

    val monitoring = MonitoringServer.start(Monitoring.default, 5775)

    // this is the edge of the world and will just block until its stopped
    Server.unsafeStart(chemist, aws)

    // if we reach these then the server process has stopped and we need
    // to cleanup the associated resources.
    (signal.set(false) >> signal.close).run
    monitoring.stop()
    dispatch.Http.shutdown()
  }
}
