package funnel
package chemist

import scalaz.concurrent.Strategy
import scalaz.concurrent.Task
import scalaz.stream.{Process, Sink, async}
import async.mutable.Signal
import scalaz.syntax.apply._
import scalaz.{-\/,\/,\/-, Either3,Left3,Middle3,Right3}
import scalaz.concurrent.Actor
import journal.Logger
import java.net.URI

trait RemoteFlask {
  def command(c: FlaskCommand): Task[Unit]
}

object LoggingRemote extends RemoteFlask {
  private lazy val log = Logger[HttpFlask]

  def flaskTemplate(path: String) =
    LocationTemplate(s"http://@host:@port/$path")
  def command(c: FlaskCommand): Task[Unit] = {
    Task.delay {
      log.info("LoggingRemote recieved: " + c)
    }
  }

}

class HttpFlask(http: dispatch.Http, repo: Repository, signal: Signal[Boolean]) extends RemoteFlask {
  import FlaskCommand._
  import LoggingRemote.flaskTemplate

  private lazy val log = Logger[HttpFlask]

  val keys: Actor[(URI, Set[Key[Any]])] = Actor[(URI, Set[Key[Any]])] {
    case (uri, keys) => repo.keySink(uri, keys).run
  }

  val errors: Actor[Error] = Actor[Error] {
    case error => repo.errorSink(error).run
  }

  val lifecycle: Actor[PlatformEvent] = Actor[PlatformEvent] {
    case ev => repo.platformHandler(ev).run
  }

  def command(c: FlaskCommand): Task[Unit] = c match {
    case Telemetry(flask) =>
      val t = monitorTelemetry(flask, keys, errors, lifecycle, signal)
      Task.delay(t.handleWith({
        case telemetry.Telemetry.MissingFrame(a, b) => for {
          _ <- Task.delay {
            val s = if (a+1 == b-1) s" ${a+1}" else s"s ${a+1}-${b-1}"
            log.error(s"Missing frame$s from Flask: ${flask}")
          }
          d <- Housekeeping.gatherAssignedTargets(Seq(flask))(http)
          _ <- Task.suspend(t)
        } yield ()
      }).runAsync(_.fold({
        case e: Exception =>
          log.error(e.getMessage)
          e.printStackTrace
        },
        _ => log.info("Telemetry terminated"))))
    case Monitor(flask, targets) =>
      monitor(flask.location, targets).void
    case Unmonitor(flask, targets) =>
      unmonitor(flask.location, targets).void
  }

  /**
   * Touch the network and do the I/O using Dispatch.
   */
  private def monitor(to: Location, targets: Seq[Target]): Task[String] = {
    import dispatch._, Defaults._
    import argonaut._, Argonaut._
    import JSON.ClustersToJSON

    val payload: Map[ClusterName, List[URI]] =
      targets.groupBy(_.cluster).mapValues(_.map(_.uri).toList)

    val uri = to.uriFromTemplate(flaskTemplate(path = "mirror"))

    val req = Task.delay(url(uri.toString) << payload.toList.asJson.nospaces) <* Task.delay(log.debug(s"submitting to $uri: $payload"))
    req.flatMap(r => fromScalaFuture(http(r OK as.String)))
  }

  /**
   * Touch the network and do the I/O using Dispatch.
   */
  private def unmonitor(to: Location, targets: Seq[Target]): Task[String] = {
    import dispatch._, Defaults._
    import argonaut._, Argonaut._
    import JSON.ClustersToJSON

    // FIXME: "safe" because we know we're passing in the default localhost
    // val host: HostAndPort = to.dns.map(_ + ":" + to.port).get
    val payload: Map[ClusterName, List[URI]] =
      targets.groupBy(_.cluster).mapValues(_.map(_.uri).toList)

    val uri = to.uriFromTemplate(flaskTemplate(path = "discard"))

    val req = Task.delay(url(uri.toString) << payload.toList.asJson.nospaces) <* Task.delay(log.debug(s"submitting to $uri: $payload"))
    req.flatMap(r => fromScalaFuture(http(r OK as.String)))
  }

  /**
   * used to contramap the Sharding handler towards the Unmonitored Either3
   * Monitored stream we get from telemetry
   *
   * Either3[Monitored, Unmonitored,Problem], I promise
   */
  private def actionsFromLifecycle(flask: FlaskID): Either3[URI, URI, (URI, String)] => PlatformEvent = {
    case Left3(id) => PlatformEvent.Unmonitored(flask, id)
    case Middle3(id) => PlatformEvent.Monitored(flask, id)
    case Right3((id,msg)) => PlatformEvent.Problem(flask, id, msg)
  }

  def monitorTelemetry(flask: Flask,
                       keys: Actor[(URI, Set[Key[Any]])],
                       errors: Actor[Error],
                       lifecycle: Actor[PlatformEvent],
                       signal: Signal[Boolean]): Task[Unit] = {

    import telemetry.Telemetry.telemetrySubscribeSocket

    log.info(s"attempting to connect to 0mq telemetry channel ${flask.telemetry.uri}")
    val lc: Actor[Either3[URI, URI, (URI,String)]] = lifecycle.contramap(actionsFromLifecycle(flask.id))
    telemetrySubscribeSocket(flask.telemetry.uri, signal, keys, errors, lc)
  }

}
