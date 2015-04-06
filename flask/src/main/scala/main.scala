package funnel
package flask

import com.aphyr.riemann.client.RiemannClient
import scala.concurrent.duration._
import scalaz.concurrent.Task
import scalaz.std.option._
import scalaz.syntax.applicative._
import scalaz.stream.{ io, Process, Sink }
import scalaz.stream.async.mutable.Signal
import knobs.{Config, Required, ClassPathResource, FileResource}
import journal.Logger
import funnel.{Events,DatapointParser,Datapoint,Names,Sigar,Monitoring,Instruments}
import funnel.riemann.Riemann
import funnel.http.{MonitoringServer,SSE}
import funnel.elastic._
import funnel.zeromq.Mirror
import java.net.URI
import zeromq._
import sockets._
import scalaz.stream._
import scalaz.\/
import messages._
import Telemetry._

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

    def countDatapoints: Sink[Task, Datapoint[Any]] =
      io.channel(_ => Task(mirrorDatapoints.increment))

    val Q = async.unboundedQueue[Telemetry]
    telemetryPublishSocket(URI.create(s"tcp://0.0.0.0:${options.telemetryPort}"), signal,
                           (I.monitoring.keys.discrete pipe keyChanges).wye(Q.dequeue)(wye.merge)).runAsync(_ => ())

    def processDatapoints(alive: Signal[Boolean])(uri: URI): Process[Task, Datapoint[Any]] =
      httpOrZmtp(alive)(uri) observe countDatapoints

    def retries(names: Names): Event =
      Monitoring.defaultRetries andThen (_ ++ Process.eval(Q.enqueueOne(Error(names))))

    val localhost = java.net.InetAddress.getLocalHost.toString

    val flaskName = options.name.getOrElse(localhost)

    // Implement key TTL
    options.metricTTL.foreach(t => runAsync(I.monitoring.keySenescence(Events.every(t)).run))

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
    val elasticTimeout   = cfg.lookup[Duration]("flask.elastic-search.connection-timeout").getOrElse(5.seconds)
    val esGroups         = cfg.lookup[List[String]]("flask.elastic-search.groups")
    val esTemplate       = cfg.lookup[String]("flask.elastic-search.template.name").getOrElse("flask")
    val esTemplateLoc    = cfg.lookup[String]("flask.elastic-search.template.location")
    val esPublishTimeout = cfg.lookup[Duration]("flask.elastic-search.minimum-publish-frequency").getOrElse(10.minutes)
    val riemannHost      = cfg.lookup[String]("flask.riemann.host")
    val riemannPort      = cfg.lookup[Int]("flask.riemann.port")
    val ttl              = cfg.lookup[Int]("flask.riemann.ttl-in-minutes").map(_.minutes)
    val riemann          = (riemannHost |@| riemannPort |@| ttl)(RiemannCfg)
    val elastic          = (elasticURL |@| elasticIx |@| elasticTy |@| esGroups)(
      ElasticCfg(_, _, _, elasticDf, esTemplate, esTemplateLoc, _, esPublishTimeout.toNanos.nanos, elasticTimeout))
    val snsErrorTopic    = cfg.require[String]("flask.sns-error-topic")
    val port             = cfg.lookup[Int]("flask.network.port").getOrElse(5775)
    val metricTTL        = cfg.lookup[Duration]("flask.metric-ttl")
    val telemetryPort        = cfg.lookup[Int]("telemetryPort").getOrElse(7391)

    Task((Options(Option(name), elastic, riemann, snsErrorTopic, port, metricTTL, telemetryPort), cfg))
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
