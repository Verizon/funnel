package funnel
package integration

import org.scalatest.FlatSpec
import scala.concurrent.duration._
import journal.Logger

class IntegrationTarget(val port: Int, delay: Duration) extends FlatSpec {
  val log = Logger[IntegrationTarget]

  log.info(s"Starting target on port $port with a delay of $delay.")

  val W = 10.seconds
  val M = Monitoring.instance
  val I = new Instruments(W, M)
  Clocks.instrument(I)

  http.MonitoringServer.start(M, port)
  Thread.sleep(delay.toMillis.toInt)

  log.info(s"Closing target on port $port.")
}
