package oncue.svc.funnel.agent

object Server {
  def main(args: Array[String]) {
    unfiltered.netty.Server.http(8080)
      .handler(RemoteInstruments)
      .run
  }
}
