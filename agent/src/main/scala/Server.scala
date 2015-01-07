package oncue.svc.funnel.agent

import oncue.svc.funnel._, http.MonitoringServer

object Server {
  def main(args: Array[String]) {
    val stop = MonitoringServer.start(Monitoring.default, 5775)

    unfiltered.netty.Server.http(8080)
      .handler(HttpInstruments)
      .run
  }
}
