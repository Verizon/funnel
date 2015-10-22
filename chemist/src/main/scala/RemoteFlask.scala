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

  def command(c: FlaskCommand): Task[Unit] =
    Task.delay {
      log.info("LoggingRemote recieved: " + c)
    }
}

class HttpFlask(http: dispatch.Http) extends RemoteFlask {
  import FlaskCommand._
  import LoggingRemote.flaskTemplate

  private lazy val log = Logger[HttpFlask]

  def command(c: FlaskCommand): Task[Unit] = {
    metrics.CommandCount.increment
    c match {
      case Monitor(flask, targets) =>
        monitor(flask.location, targets).void

      case Unmonitor(flask, targets) =>
        unmonitor(flask.location, targets).void
    }
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

    val payload: Map[ClusterName, List[URI]] =
      targets.groupBy(_.cluster).mapValues(_.map(_.uri).toList)

    val uri = to.uriFromTemplate(flaskTemplate(path = "discard"))

    val req = Task.delay(url(uri.toString) << payload.toList.asJson.nospaces) <* Task.delay(log.debug(s"submitting to $uri: $payload"))
    req.flatMap(r => fromScalaFuture(http(r OK as.String)))
  }

}
