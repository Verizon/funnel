package funnel
package integration

import org.scalatest.FlatSpec
import scala.concurrent.duration._
import journal.Logger

object IntegrationTarget {
  val log = Logger[IntegrationTarget.type]

  def start(port: Int): http.MonitoringServer = {
    val W = 10.seconds
    val M = Monitoring.instance
    val I = new Instruments(W, M)
    Clocks.instrument(I)
    http.MonitoringServer.start(M, port)
  }
}
