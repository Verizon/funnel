package funnel
package flask

import scala.concurrent.duration._
import scalaz.concurrent.Strategy
import scalaz.concurrent.Task
import scalaz.std.option._
import scalaz.syntax.applicative._
import scalaz.stream.{ io, Process, Sink, channel }
import scalaz.stream.async.mutable.{Queue,Signal}
import knobs.{Config, Required, ClassPathResource, FileResource}
import journal.Logger
import funnel.{Events,DatapointParser,Datapoint,Names,Sigar,Monitoring,Instruments}
import funnel.http.{MonitoringServer,SSE}
import funnel.elastic._
import funnel.zeromq.Mirror
import java.net.URI
import zeromq._
import sockets._
import scalaz.stream._
import scalaz.\/
import telemetry.Telemetry._

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

  val Selfie = Monitoring.instance
  val ISelfie = new Instruments(1.minute, Selfie)
  val mirrorDatapoints = ISelfie.counter("mirror/datapoints")

  val S = MonitoringServer.start(I.monitoring, options.funnelPort)
  val SelfServing = MonitoringServer.start(ISelfie.monitoring, options.selfiePort)

  lazy val signal: Signal[Boolean] = scalaz.stream.async.signalOf(true)(Strategy.Executor(Monitoring.serverPool))

  private def shutdown(server: MonitoringServer): Unit = {
    server.stop()
    signal.set(false).flatMap(_ => signal.close).run
  }

  private def runAsync(p: Task[Unit]): Unit = p.runAsync(_.fold(e => {
    e.printStackTrace()
    log.error(s"Flask error in runAsync(): Exception $e - ${e.getMessage}")
    log.error(e.getStackTrace.toList.mkString("\n","\t\n",""))
  }, identity _))

  private def httpOrZmtp(alive: Signal[Boolean], Q: Queue[Telemetry])(uri: URI): Process[Task,Datapoint[Any]] =
    Option(uri.getScheme).map(_.toLowerCase) match {
      case Some("http")       => SSE.readEvents(uri, Q)
      case Some("zeromq+tcp") => Mirror.from(alive, Q)(uri)
      case unknown            => Process.fail(
        new RuntimeException(s"Unknown URI scheme submitted. scheme = $unknown"))
    }

  def run(args: Array[String]): Unit = {

    def countDatapoints: Sink[Task, Datapoint[Any]] =
      channel.lift(_ => Task(mirrorDatapoints.increment))


    // Determine whether to generate system statistics for the local host
    for {
      b <- options.collectLocalMetrics
      t <- options.localMetricFrequency
    }{
      implicit val duration = t.seconds
      Sigar(ISelfie).foreach(_.instrument)
      JVM.instrument(ISelfie)
    }

    val Q = async.unboundedQueue[Telemetry](Strategy.Executor(funnel.Monitoring.serverPool))

    log.info("Booting the key mirroring process...")
    runAsync(
      telemetryPublishSocket(
        URI.create(s"zeromq+tcp://0.0.0.0:${options.telemetryPort}"), signal,
        Q.dequeue)
    )

    def processDatapoints(alive: Signal[Boolean])(uri: URI): Process[Task, Datapoint[Any]] =
      httpOrZmtp(alive, Q)(uri) observe countDatapoints

    def retries(names: Names): Event = {
      val retries = Events.takeEvery(options.retriesDuration, options.maxRetries)

      retries andThen (_ ++ Process.eval[Task, Unit] {
                         Q.enqueueAll(Seq(Error(names), Problem(names.theirs, "there wasn't an error")))
                           .flatMap(_ => Task.delay(log.error("stopped mirroring: " + names.toString)))
                       })
    }

    import java.net.InetAddress
    val flaskHost = InetAddress.getLocalHost.getHostName
    val flaskName = options.name.getOrElse(flaskHost)
    val flaskCluster = options.cluster.getOrElse(s"flask-${oncue.svc.funnel.BuildInfo.version}")

    options.metricTTL.foreach { t =>
      log.info("Booting the key senescence...")
      runAsync(I.monitoring.keySenescence(Events.every(t), I.monitoring.distinctKeys).run)
    }

    log.info("Booting the mirroring process...")
    runAsync(I.monitoring.processMirroringEvents(processDatapoints(signal), Q, flaskName, retries))

    log.info("Mirroring own Funnel instance...")
    List(s"http://$flaskHost:${options.selfiePort}/stream/previous",
         s"http://$flaskHost:${options.selfiePort}/stream/now?kind=traffic").foreach { s =>
      I.monitoring.mirroringQueue.enqueueOne(funnel.Mirror(new URI(s), flaskCluster)).run
    }

    options.elastic.foreach { elastic =>
      log.info("Booting the elastic search sink...")
      runAsync(Elastic(I.monitoring).publish(flaskName, flaskCluster)(elastic))
    }
  }
}
