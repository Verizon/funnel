package funnel
package chemist
package aws

import scalaz.concurrent.Task
import http.MonitoringServer

object Main {
  def main(args: Array[String]): Unit = {

    val chemist = new AwsChemist

    val aws = new Aws {
      val config = (for {
        a <- defaultKnobs
        b <- knobs.aws.config
      } yield Config.readConfig(a ++ b)).run
    }

    val monitoring = MonitoringServer.start(Monitoring.default, 5775)

    Server.start(chemist, aws).onFinish(_ => Task.delay {
      monitoring.stop()
      dispatch.Http.shutdown()
    }).run
  }
}
