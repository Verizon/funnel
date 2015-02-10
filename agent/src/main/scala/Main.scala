package oncue.svc.funnel
package agent

import knobs._
import scalaz.{\/,-\/,\/-}
import java.io.File
import journal.Logger
import scalaz.std.option._
import scalaz.concurrent.Task
import java.net.{InetAddress,URI}
import scalaz.syntax.applicative._
import scala.concurrent.duration._
import oncue.svc.funnel.zeromq._, sockets._
import agent.zeromq._
// import agent.http._
// import agent.statsd._
// import agent.nginx.Nginx

object Main {
  private val log = Logger[Main.type]

  case class HttpConfig(host: String, port: Int)
  case class StatsdConfig(port: Int, prefix: String)
  case class ProxyConfig(host: String, port: Int, socket: String)
  case class NginxConfig(frequency: Duration)

  case class Options(
    http: Option[HttpConfig],
    statsd: Option[StatsdConfig],
    proxy: Option[ProxyConfig],
    nginx: Option[NginxConfig]
  )

  // remove this once the PR to knobs has been accepted.
  implicit val configuredDuration: Configured[Duration] = new Configured[Duration]{
    def apply(a: CfgValue) = a match {
      case CfgText(b) =>
        \/.fromTryCatchNonFatal(Duration(b)
          ).fold(_ => Option.empty,Option(_))
      case _ => None
    }
  }

  def main(args: Array[String]): Unit = {
    log.info("Loading agent configuation from disk.")

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
    val options: Options = config.map { cfg =>
      val httpHost    = cfg.lookup[String]("agent.http.host")
      val httpPort    = cfg.lookup[Int]("agent.http.port")
      val statsdPort  = cfg.lookup[Int]("agent.statsd.port")
      val statsdPfx   = cfg.lookup[String]("agent.statsd.prefix")
      val proxySocket = cfg.lookup[String]("agent.proxy.socket")
      val proxyHost   = cfg.lookup[String]("aws.network.local-ipv4")
        .orElse(cfg.lookup[String]("agent.proxy.host"))
      val proxyPort   = cfg.lookup[Int]("agent.proxy.port")
      val nginxFreq   = cfg.lookup[Duration]("agent.nginx.poll-frequency")

      Options(
        http   = (httpHost |@| httpPort)(HttpConfig),
        statsd = (statsdPort |@| statsdPfx)(StatsdConfig),
        proxy  = (proxyHost |@| proxyPort |@| proxySocket)(ProxyConfig),
        nginx  = (nginxFreq)(NginxConfig)
      )
    }.run

    /**
     * Setup the instruments instance that will be used by the remote
     * instrument bridges (e.g. http, statsd etc).
     */
    val I = new Instruments(1.minute, Monitoring.default)

    /**
     * Attempt to create and bind endpoints for both the domain socket
     * AND the TCP socket going outbound. If this cannot be achived, the
     * agent is critically failed.
     */
    options.proxy.foreach { proxy =>
      log.info("Launching the 0mq proxy.")

      val (i,o) =
        (for {
          y <- Endpoint(pull &&& bind, new URI(s"ipc://${proxy.socket}"))
          z <- Endpoint(publish &&& bind, new URI(s"tcp://${proxy.host}:${proxy.port}"))
        } yield (y,z)).getOrElse(sys.error("Bootstrapping the agent was not possible due to a fatal error."))

      // start the streaming 0MQ proxy
      new Proxy(i,o).task.runAsync(_.fold(
        e => log.error(s"0mq proxy resulted in failure: $e"),
        _ => ()
      ))

      log.info(s"Enabling 0mq metric publication to ${proxy.socket}.")
      /**
       * For metrics that are produced by the agent itself, publish them to the
       * local domain socket like any other application such that they will
       * then be consumed by the very same agent proxy to TCP.
       *
       * This is a bit of a hack, but it works!
       */
      Publish.toUnixSocket(path = s"${proxy.socket}")
    }

    // start the statsd instruments server
    options.statsd.foreach { stats =>
      log.info("Launching the StatsD instrument interface.")
      statsd.Server(stats.port, stats.prefix)(I).runAsync {
        case -\/(e) => log.error(s"Unable to start the StatsD interface: ${e.getMessage}")
        case _      => ()
      }
    }

    // start the http instruments server
    options.http.foreach { config =>
      log.info("Launching the HTTP instrument interface.")
      unfiltered.netty.Server.http(config.port, config.host)
        .handler(new http.Server(I))
        .run
    }
  }
}
