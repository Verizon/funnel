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

  def command(c: FlaskCommand): Task[Unit] = {
    Task.delay {
      log.info("LoggingRemote recieved: " + c)
    }
  }

}

import org.http4s.client.Client

class HttpFlask(http: Client, repo: Repository, signal: Signal[Boolean]) extends RemoteFlask {
  import FlaskCommand._

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
      Task.delay(t.runAsync(_.fold(
        e => {
          log.error(e.getMessage)
          e.printStackTrace
        },
        _ => log.info("Telemetry terminated"))))
    case Monitor(flask, targets) =>
      monitor(flask.location, targets).void
    case Unmonitor(flask, targets) =>
      unmonitor(flask.location, targets).void
  }

  import org.http4s.argonaut._
  import org.http4s.Request
  import org.http4s.Method


  /**
   * Touch the network and do the I/O using HTTP.
   */
  private def monitor(to: Location, targets: Seq[Target]): Task[String] = {
    import argonaut._, Argonaut._
    import JSON.ClustersToJSON

    // FIXME: "safe" because we know we're passing in the default localhost
    // val host: HostAndPort = to.dns.map(_ + ":" + to.port).get
    val payload: Map[ClusterName, List[URI]] =
      targets.groupBy(_.cluster).mapValues(_.map(_.uri).toList)

    val uri = to.asUri(path = "mirror")

    for {
      req <- Request(uri = uri, method = Method.POST) withBody payload.toList.asJson
      _   <- Task.delay(log.debug(s"submitting to $uri: ${payload.toList.asJson.nospaces}"))
      res <- http.prepAs[String](req)
    } yield res
  }

  /**
   * Touch the network and do the I/O using HTTP.
   */
  private def unmonitor(to: Location, targets: Seq[Target]): Task[String] = {
    import argonaut._, Argonaut._
    import JSON.ClustersToJSON

    // FIXME: "safe" because we know we're passing in the default localhost
    // val host: HostAndPort = to.dns.map(_ + ":" + to.port).get
    val payload: Map[ClusterName, List[URI]] =
      targets.groupBy(_.cluster).mapValues(_.map(_.uri).toList)

    val uri = to.asUri(path = "discard")

    for {
      req <- Request(uri = uri, method = Method.POST) withBody payload.toList.asJson
      _   <- Task.delay(log.debug(s"submitting to $uri: ${payload.toList.asJson.nospaces}"))
      res <- http.prepAs[String](req)
    } yield res
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

    log.info(s"attempting to monitor telemetry on ${flask.telemetry.asJavaURI()}")
    val lc: Actor[Either3[URI, URI, (URI,String)]] = lifecycle.contramap(actionsFromLifecycle(flask.id))
    telemetrySubscribeSocket(flask.telemetry.asJavaURI(), signal, keys, errors, lc)
  }

}
