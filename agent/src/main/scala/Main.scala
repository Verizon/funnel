package oncue.svc.funnel
package agent

import knobs._
import scalaz.\/
import java.io.File
import journal.Logger
import scalaz.concurrent.Task
import java.net.{InetAddress,URI}
import oncue.svc.funnel.zeromq._, sockets._
import oncue.svc.funnel.agent.zeromq._
import oncue.svc.funnel.agent.http._
import oncue.svc.funnel.agent.statsd._

object Main {
  private val log = Logger[Main.type]

  case class Options(
    httpHost: String,
    httpPort: Int,
    proxySocket: String,
    proxyHost: String,
    proxyPort: Int
  )

  def main(args: Array[String]): Unit = {
    val config: Task[Config] = for {
      a <- knobs.loadImmutable(List(Required(
        FileResource(new File("/usr/share/oncue/etc/agent.cfg")) or
        ClassPathResource("oncue/agent.cfg"))))
      b <- knobs.aws.config
    } yield a ++ b

    /**
     * Create a typed set of options using knobs.
     * For the proxy host, try looking up the local address of this
     * particular node and fallback to the config, and then all
     * known interfaces host if that fails.
     */
    val options = config.map { cfg =>
      Options(
        cfg.require[String]("agent.http.host"),
        cfg.lookup[Int]("agent.http.port").getOrElse(8080),
        cfg.require[String]("agent.proxy.socket"),
        cfg.lookup[String]("aws.network.local-ipv4")
          .orElse(cfg.lookup[String]("agent.proxy.host"))
          .getOrElse("0.0.0.0"),
        cfg.lookup[Int]("agent.proxy.port").getOrElse(8080)
      )
    }.run

    /**
     * attempt to create and bind endpoints for both the domain socket
     * AND the TCP socket going outbound. If this cannot be achived, the
     * agent is critically failed.
     */
    val (i,o) =
      (for {
        y <- Endpoint(pull &&& bind, new URI(s"ipc://${options.proxySocket}"))
        z <- Endpoint(publish &&& bind, new URI(s"tcp://${options.proxyHost}:${options.proxyPort}"))
      } yield (y,z)).getOrElse(sys.error("Bootstrapping the agent was not possible due to a fatal error."))

    // start the streaming 0MQ proxy
    new Proxy(i,o).task.runAsync(_.fold(
      e => log.error(s"0mq proxy resulted in failure: $e"),
      _ => ()
    ))

    /**
     * For metrics that are produced by the agent itself, publish them to the
     * local domain socket like any other application such that they will
     * then be consumed by the very same agent proxy to TCP.
     *
     * This is a bit of a hack, but it works!
     */
    Publish.toUnixSocket(path = s"${options.proxySocket}")

    // start the remote instruments server
    unfiltered.netty.Server.http(options.httpPort, options.httpHost)
      .handler(http.Server)
      .run
  }
}
