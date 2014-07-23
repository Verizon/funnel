package intelmedia.ws.funnel
package utensil

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
  * How to use: Modify oncue/utensil.cfg on the classpath
  * and run from the command line.
  *
  * Or pass the location of the config file as a command line argument.
  */
object Utensil extends CLI {
  // private val stop = new java.util.concurrent.atomic.AtomicBoolean(false)
  private def shutdown(server: MonitoringServer, R: RiemannClient): Unit = {
    server.stop()
    // nice little hack to get make it easy to just hit return and shutdown
    // this running example
    // stop.set(true)
    R.disconnect
  }

  import com.amazonaws.auth.{AWSCredentialsProvider, AWSCredentials}
  import com.amazonaws.services.sns.AmazonSNSClient
  import com.amazonaws.regions.{Region, Regions}
  import com.amazonaws.services.sns.model.{CreateTopicRequest, PublishRequest}
  import Events.Event
  import Riemann.Names
  import scalaz.\/._

  private def giveUp(names: Names, cfg: Config, log: String => Unit) =
   if (cfg.lookup[String]("aws.accessKey").isDefined) {
     Process.eval(for {
       credsProvider <- Task(new AWSCredentialsProvider {
         def getCredentials = new AWSCredentials {
           def getAWSAccessKeyId = cfg.require[String]("aws.accessKey")
           def getAWSSecretKey = cfg.require[String]("aws.secretKey")
         }
         val refresh = ()
       })
       creds <- Task(credsProvider.getCredentials).attempt
       ee <- creds.fold(_ => Task(right(())),
                        _ => Task {
            val snsClient = new AmazonSNSClient
            snsClient.setRegion(Region.getRegion(Regions.fromName(cfg.require[String]("aws.region"))))
            val req = new CreateTopicRequest(cfg.require[String]("aws.snsTopic"))
            val res = snsClient.createTopic(req)
            val arn = res.getTopicArn
            val msg = s"${names.mine} gave up on ${names.kind} server ${names.theirs}"
            val preq = new PublishRequest(arn, msg)
            val pres = snsClient.publish(preq)
            log(s"Posted $pres to SNS $arn")
          }.attempt)
       _ <- ee.fold(e => Task(log(s"Error posting to SNS: $e")), _ => Task(()))
     } yield ())
   } else Process.halt

  private def errorAndQuit(options: Options, f: () => Unit): Unit = {
    val msg = s"# Riemann is not running at the specified location (${options.riemann.host}:${options.riemann.port}) #"
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
      val logger = LoggerFactory.getLogger("utensil")
      implicit val logPool = Strategy.Executor(java.util.concurrent.Executors.newFixedThreadPool(1))
      val L = Actor.actor((s: String) => logger.info(s))

      implicit val log: String => Unit = s => L(s)

      val M = Monitoring.default
      val S = MonitoringServer.start(M, options.funnelPort)

      // Determine whether to generate system statistics for the local host
      cfg.lookup[Int]("localHostMonitorFrequencySeconds").foreach { t =>
        implicit val duration = t.seconds
        Sigar.instrument(new Instruments(1 minute, M))
      }

      val R = RiemannClient.tcp(options.riemann.host, options.riemann.port)
      try {
        R.connect() // urgh. Give me stregth!
      } catch {
        case e: java.io.IOException => {
          errorAndQuit(options,() => shutdown(S,R))
        }
      }

      def utensilRetries(names: Names): Event = Riemann.defaultRetries andThen (_ ++ giveUp(names, cfg, log))

      val localhost = java.net.InetAddress.getLocalHost.toString

      Riemann.mirrorAndPublish(
        M, options.riemannTTL.toSeconds.toFloat, utensilRetries)(
        R, s"${options.riemann.host}:${options.riemann.port}", utensilRetries)(SSE.readEvents)(
        S.mirroringSources, cfg.lookup[String]("funnelName").getOrElse(localhost))(log
          ).runAsync(_.fold(e => log(s"[ERROR] $e - ${e.getMessage}"), identity _))

      // println
      // println("Press [Enter] to quit...")
      // println

      // val _ = readLine()

      // shutdown(S,R)
    }
  }

}

import java.net.URL
import scala.concurrent.duration._
import java.io.File

trait CLI {

  case class RiemannHostPort(host: String, port: Int)

  case class Options(
    riemann: RiemannHostPort = RiemannHostPort("localhost", 5555),
    riemannTTL: Duration = 5 minutes,
    funnelPort: Int = 5775,
    transport: DatapointParser = SSE.readEvents _
  )

  def run(args: Array[String])(f: (Options, Config) => Unit): Unit = {

    val config =
      knobs.loadImmutable(List(Required(FileResource(new File("/usr/share/oncue/etc/utensil.cfg"))))) or
      knobs.loadImmutable(List(Required(ClassPathResource("oncue/utensil.cfg"))))

    config.flatMap { cfg =>
      val port = cfg.lookup[Int]("funnelPort").getOrElse(5775)
      val name = cfg.lookup[String]("funnelName").getOrElse("Funnel")
      val riemannHost = cfg.lookup[String]("riemannHost").getOrElse("localhost")
      val riemannPort = cfg.lookup[Int]("riemannPort").getOrElse(5555)
      val ttl = cfg.lookup[Int]("riemannTTLMinutes").getOrElse(5).minutes
      Task(f(Options(RiemannHostPort(riemannHost, riemannPort), ttl, port), cfg))
    }.run
  }
}

