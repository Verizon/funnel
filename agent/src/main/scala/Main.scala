package oncue.svc.funnel
package agent

import journal.Logger
import zeromq._, sockets._
import java.net.{InetAddress,URI}
import scalaz.\/

object Main {
  private val log = Logger[Main.type]

  def main(args: Array[String]): Unit = {
    val (a,i,o) =
      (for {
        x <- \/.fromTryCatchThrowable[String,Exception](InetAddress.getLocalHost.getHostAddress)
        y <- Endpoint(pull &&& bind, new URI("ipc:///var/run/funnel.socket"))
        z <- Endpoint(publish &&& bind, new URI(s"tcp://$x:7390"))
      } yield (x,y,z)).getOrElse(sys.error("Bootstrapping the agent was not possible due to a fatal error."))

    // start the streaming 0MQ proxy
    new Proxy(i,o).task.runAsync(_.fold(
      e => log.error(s"0mq proxy resulted in failure: $e"),
      _ => ()
    ))

    // start the remote instruments server
    unfiltered.netty.Server.http(8080)
      .handler(HttpInstruments)
      .run
  }
}
