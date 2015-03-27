package funnel
package flask

import com.aphyr.riemann.client.RiemannClient
import scala.concurrent.duration._
import scalaz.concurrent.Task
import scalaz.std.option._
import scalaz.syntax.applicative._
import scalaz.stream.{ io, Process, Sink }
import scalaz.stream.async.mutable.Signal
import com.amazonaws.auth.{AWSCredentialsProvider, AWSCredentials}
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.sns.{AmazonSNSClient,AmazonSNS}
import com.amazonaws.regions.{Region, Regions}
import journal.Logger
import funnel.{Events,DatapointParser,Datapoint,Names,Sigar,Monitoring,Instruments}
import funnel.riemann.Riemann
import funnel.http.{MonitoringServer,SSE}
import funnel.elastic._
import funnel.zeromq.Mirror
import java.net.URI

/**
  * How to use: Modify oncue/flask.cfg on the classpath
  * and run from the command line.
  *
  * Or pass the location of the config file as a command line argument.
  */
class Flask(options: Options, val I: Instruments) {
  import Events.Event
  import scalaz.\/._

  val log = Logger[this.type]

  val mirrorDatapoints = I.counter("mirror/datapoints")

  val S = MonitoringServer.start(I.monitoring, options.funnelPort)

  lazy val signal: Signal[Boolean] = scalaz.stream.async.signalOf(true)

  private def shutdown(server: MonitoringServer, R: RiemannClient): Unit = {
    server.stop()
    signal.set(false).flatMap(_ => signal.close).run
    R.disconnect
  }

  private def giveUp(names: Names, sns: AmazonSNS) = {
    val msg = s"${names.mine} gave up on ${names.kind} server ${names.theirs}"
    Process.eval(SNS.publish(options.snsErrorTopic, msg)(sns))
  }

  private def riemannErrorAndQuit(rm: RiemannCfg, f: () => Unit): Unit = {
    val msg = s"# Riemann is not running at the specified location (${rm.host}:${rm.port}) #"
    val padding = "#" * msg.length
    Console.err.println(padding)
    Console.err.println(msg)
    Console.err.println(padding)
    f()
    System.exit(1)
  }

  private def runAsync(p: Task[Unit]): Unit = p.runAsync(_.fold(e => {
    e.printStackTrace()
    log.error(s"Flask error in runAsync(): Exception $e - ${e.getMessage}")
    log.error(e.getStackTrace.toList.mkString("\n","\t\n",""))
  }, identity _))

  private def httpOrZmtp(alive: Signal[Boolean])(uri: URI): Process[Task,Datapoint[Any]] =
    Option(uri.getScheme).map(_.toLowerCase) match {
      case Some("http") => SSE.readEvents(uri)
      case Some("tcp")  => Mirror.from(alive)(uri)
      case _            => Process.fail(new RuntimeException("Unknown URI scheme submitted."))
    }

  def run(args: Array[String]): Unit = {
    val Q = SNS.client(
      options.awsCredentials,
      options.awsProxyHost,
      options.awsProxyPort,
      options.awsProxyProtocol,
      Region.getRegion(Regions.fromName(options.awsRegion))
    )

    def countDatapoints: Sink[Task, Datapoint[Any]] =
      io.channel(_ => Task(mirrorDatapoints.increment))

    def processDatapoints(alive: Signal[Boolean])(uri: URI): Process[Task, Datapoint[Any]] =
      httpOrZmtp(alive)(uri) observe countDatapoints

    def retries(names: Names): Event =
      Monitoring.defaultRetries andThen (_ ++ giveUp(names, Q))

    val localhost = java.net.InetAddress.getLocalHost.toString

    val flaskName = options.name.getOrElse(localhost)

    runAsync(I.monitoring.processMirroringEvents(processDatapoints(signal), flaskName, retries))

    options.elastic.foreach { elastic =>
      runAsync(Elastic(I.monitoring).publish(flaskName)(elastic))
    }

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
        I.monitoring, riemann.ttl.toSeconds.toFloat)(
        R, s"${riemann.host}:${riemann.port}", retries)(flaskName))
    }
  }
}

object Main {
  import java.io.File
  import knobs.{ ClassPathResource, Config, FileResource, Required }

  val config: Task[Config] = for {
    a <- knobs.loadImmutable(List(Required(
      FileResource(new File("/usr/share/oncue/etc/flask.cfg")) or
        ClassPathResource("oncue/flask.cfg"))))
    b <- knobs.aws.config
  } yield a ++ b

  val (options, cfg) = config.flatMap { cfg =>
    val name             = cfg.lookup[String]("flask.name").getOrElse("flask")
    val elasticURL       = cfg.lookup[String]("flask.elastic-search.url")
    val elasticIx        = cfg.lookup[String]("flask.elastic-search.index-name")
    val elasticTy        = cfg.lookup[String]("flask.elastic-search.type-name")
    val elasticDf        =
      cfg.lookup[String]("flask.elastic-search.partition-date-format").getOrElse("yyyy.MM.dd")
    val elasticTimeout   = cfg.lookup[Int]("flask.elastic-search.connection-timeout-in-ms").getOrElse(5000)
    val riemannHost      = cfg.lookup[String]("flask.riemann.host")
    val riemannPort      = cfg.lookup[Int]("flask.riemann.port")
    val ttl              = cfg.lookup[Int]("flask.riemann.ttl-in-minutes").map(_.minutes)
    val riemann          = (riemannHost |@| riemannPort |@| ttl)(RiemannCfg)
    val elastic          = (elasticURL |@| elasticIx |@| elasticTy)(
      ElasticCfg(_, _, _, elasticDf, elasticTimeout))
    val snsErrorTopic    = cfg.require[String]("flask.sns-error-topic")
    val awsAccessKey     = cfg.require[String]("aws.access-key")
    val awsSecretKey     = cfg.require[String]("aws.secret-key")
    val awsCredentials   = new BasicAWSCredentials(awsAccessKey, awsSecretKey)
    val awsProxyHost     = cfg.lookup[String]("aws.proxy-host")
    val awsProxyPort     = cfg.lookup[Int]("aws.proxy-port")
    val awsProxyProtocol = cfg.lookup[String]("aws.proxy-protocol")
    val awsRegion        = cfg.require[String]("aws.region")
    val port             = cfg.lookup[Int]("flask.network.port").getOrElse(5775)
    Task((Options(Option(name), elastic, riemann, snsErrorTopic, awsCredentials, awsProxyHost,
      awsProxyPort, awsProxyProtocol, awsRegion, port), cfg))
  }.run

  val I = new Instruments(1.minute)

  // Determine whether to generate system statistics for the local host
  for {
    b <- cfg.lookup[Boolean]("flask.collect-local-metrics") if b == true
    t <- cfg.lookup[Int]("flask.local-metric-frequency")
  }{
    implicit val duration = t.seconds
    Sigar(I).foreach { s =>
      s.instrument
    }
  }

  val app = new Flask(options, I)

  def main(args: Array[String]) = app.run(args)
}
