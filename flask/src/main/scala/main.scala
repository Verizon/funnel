package intelmedia.ws.funnel
package flask

import riemann.Riemann
import http.{MonitoringServer,SSE}
import com.aphyr.riemann.client.RiemannClient
import scalaz.concurrent.Task
import scalaz.stream.Process
import scala.concurrent.duration._
import org.slf4j.LoggerFactory
import java.io.File
import knobs.{Config, Required, ClassPathResource, FileResource}

/**
  * How to use: Modify oncue/flask.cfg on the classpath
  * and run from the command line.
  *
  * Or pass the location of the config file as a command line argument.
  */
object Main extends CLI {
  private def shutdown(server: MonitoringServer, R: RiemannClient): Unit = {
    server.stop()
    R.disconnect
  }

  import com.amazonaws.auth.{AWSCredentialsProvider, AWSCredentials}
  import com.amazonaws.services.sns.AmazonSNSClient
  import com.amazonaws.auth.BasicAWSCredentials
  import com.amazonaws.regions.{Region, Regions}
  import Events.Event
  import scalaz.\/._
  import oncue.svc.funnel.aws.SNS

  private def giveUp(names: Names, cfg: Config, sns: AmazonSNSClient, log: String => Unit) = {
    val msg = s"${names.mine} gave up on ${names.kind} server ${names.theirs}"
    Process.eval(SNS.publish(cfg.require[String]("flask.sns-error-topic"), msg)(sns))
  }

  private def riemannErrorAndQuit(rm: RiemannHostPort, f: () => Unit): Unit = {
    val msg = s"# Riemann is not running at the specified location (${rm.host}:${rm.port}) #"
    val padding = (for(_ <- 1 to msg.length) yield "#").mkString
    Console.err.println(padding)
    Console.err.println(msg)
    Console.err.println(padding)
    f()
    System.exit(1)
  }

  def main(args: Array[String]): Unit = {
    run(args){ (options, cfg) =>

      import scalaz.concurrent._
      val logger = LoggerFactory.getLogger("flask")
      implicit val logPool =
        Strategy.Executor(java.util.concurrent.Executors.newFixedThreadPool(1))
      val L = Actor.actor((s: String) => logger.info(s))

      implicit val log: String => Unit = s => L(s)

      val Q = SNS.client(
        new BasicAWSCredentials(
          cfg.require[String]("aws.access-key"),
          cfg.require[String]("aws.secret-key")),
        cfg.lookup[String]("aws.proxy-host"),
        cfg.lookup[Int]("aws.proxy-port"),
        cfg.lookup[String]("aws.proxy-protocol"),
        Region.getRegion(Regions.fromName(cfg.require[String]("aws.region")))
      )

      val M = Monitoring.default
      val S = MonitoringServer.start(M, options.funnelPort)

      // Determine whether to generate system statistics for the local host
      for {
        b <- cfg.lookup[Boolean]("flask.collect-local-metrics") if b == true
        t <- cfg.lookup[Int]("flask.local-metric-frequency")
      }{
        implicit val duration = t.seconds
        Sigar(new Instruments(1.minute, M)).foreach { s =>
          s.instrument
          // Flask.instrument(M, s)
        }
      }

      def retries(names: Names): Event =
        Monitoring.defaultRetries andThen (_ ++ giveUp(names, cfg, Q, log))

      val localhost = java.net.InetAddress.getLocalHost.toString

      val myName = cfg.lookup[String]("flask.name").getOrElse(localhost)

      runAsync(M.processMirroringEvents(SSE.readEvents, myName, retries))

      options.riemann.foreach { riemann =>
        val R = RiemannClient.tcp(riemann.host, riemann.port)
        try {
          R.connect() // urgh. Give me stregth!
        } catch {
          case e: java.io.IOException => {
            riemannErrorAndQuit(riemann, () => shutdown(S,R))
          }
        }

        runAsync(Riemann.publishToRiemann(
          M, options.riemannTTL.toSeconds.toFloat)(
          R, s"${riemann.host}:${riemann.port}", retries)(myName))
      }
    }
  }

  def runAsync(p: Task[Unit])(implicit log: String => Unit): Unit = p.runAsync(_.fold(e => {
    e.printStackTrace()
    log(s"[ERROR] $e - ${e.getMessage}")
    log(e.getStackTrace.toList.mkString("\n","\t\n",""))
  }, identity _))

}

import java.net.URL
import scala.concurrent.duration._
import java.io.File

trait CLI {

  case class RiemannHostPort(host: String, port: Int)

  case class Options(
    riemann: Option[RiemannHostPort] = None,
    riemannTTL: Duration = 5 minutes,
    funnelPort: Int = 5775,
    transport: DatapointParser = SSE.readEvents _
  )

  def run(args: Array[String])(f: (Options, Config) => Unit): Unit = {

    import scalaz.syntax.applicative._
    import scalaz.std.option._

    val config: Task[Config] = for {
      a <- knobs.loadImmutable(List(Required(
        FileResource(new File("/usr/share/oncue/etc/flask.cfg")) or
        ClassPathResource("oncue/flask.cfg"))))
      b <- knobs.aws.config
    } yield a ++ b

    config.flatMap { cfg =>
      val port        = cfg.lookup[Int]("flask.network.port").getOrElse(5775)
      val name        = cfg.lookup[String]("flask.name").getOrElse("flask")
      val riemannHost = cfg.lookup[String]("flask.riemann.host")
      val riemannPort = cfg.lookup[Int]("flask.riemann.port")
      val ttl         = cfg.lookup[Int]("flask.riemann.ttl-in-minutes").getOrElse(5).minutes
      val riemann = (riemannHost |@| riemannPort)(RiemannHostPort)
      Task(f(Options(riemann, ttl, port), cfg))
    }.run
  }
}

