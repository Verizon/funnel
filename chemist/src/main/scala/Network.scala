package funnel
package chemist

import scalaz.concurrent.Strategy
import scalaz.concurrent.Task
import scalaz.stream.{Process, Sink, async}
import async.mutable.Signal
import scalaz.syntax.apply._
import scalaz.{-\/,\/,\/-}
import scalaz.concurrent.Actor
import journal.Logger
import java.net.URI

trait RemoteFlask {
  def command(c: FlaskCommand): Task[Unit]
}

object LoggingRemote extends RemoteFlask {
  private lazy val log = Logger[HttpFlask]

  def command(c: FlaskCommand): Task[Unit] = {
    Task.delay {
      log.info("LoggingRemote recieved: " + c)
    }
  }

}

class HttpFlask(http: dispatch.Http, repo: Repository, signal: Signal[Boolean]) extends RemoteFlask {
  import FlaskCommand._

  private lazy val log = Logger[HttpFlask]

  val keys: Actor[(URI, Set[Key[Any]])] = Actor[(URI, Set[Key[Any]])] {
    case (uri, keys) =>
      log.warn(s"!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! KEYS via telemetry: $uri -> ${keys.size}")
      repo.keySink(uri, keys).run
  }

  val errors: Actor[Error] = Actor[Error] {
    case error =>
      log.error(s"!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! ERROR via telemetry: $error")
      repo.errorSink(error).run
  }

  val lifecycle: Actor[PlatformEvent] = Actor[PlatformEvent] {
    case ev =>
      log.warn(s"!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! LIFECYCLE via telemetry: $ev")
      repo.platformHandler(ev).run
  }

  def command(c: FlaskCommand): Task[Unit] = c match {
    case Telemetry(flask) =>
      val t = monitorTelemetry(flask, keys, errors, lifecycle, signal)
      Task.delay(t.runAsync(_ => ()))
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

    // FIXME: "safe" because we know we're passing in the default localhost
    // val host: HostAndPort = to.dns.map(_ + ":" + to.port).get
    val payload: Map[ClusterName, List[URI]] =
      targets.groupBy(_.cluster).mapValues(_.map(_.uri).toList)

    val uri = to.asURI(path = "mirror")

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

    val uri = to.asURI(path = "discard")

    val req = Task.delay(url(uri.toString) << payload.toList.asJson.nospaces) <* Task.delay(log.debug(s"submitting to $uri: $payload"))
    req.flatMap(r => fromScalaFuture(http(r OK as.String)))
  }

  /**
   * used to contramap the Sharding handler towards the Unmonitored \/
   * Monitored stream we get from telemetry
   *
   * Monitored \/ Unmonitored, I promise
   */
  private def actionsFromLifecycle(flask: FlaskID): URI \/ URI => PlatformEvent = {
    case -\/(id) => PlatformEvent.Unmonitored(flask, id)
    case \/-(id) => PlatformEvent.Monitored(flask, id)
  }

  def monitorTelemetry(flask: Flask,
                       keys: Actor[(URI, Set[Key[Any]])],
                       errors: Actor[Error],
                       lifecycle: Actor[PlatformEvent],
                       signal: Signal[Boolean]): Task[Unit] = {

    import telemetry.Telemetry.telemetrySubscribeSocket

    log.info(s"attempting to monitor telemetry on ${flask.telemetry.asURI()}")
    val lc: Actor[URI \/ URI] = lifecycle.contramap(actionsFromLifecycle(flask.id))
    telemetrySubscribeSocket(flask.telemetry.asURI(), signal, keys, errors, lc)
  }

}
