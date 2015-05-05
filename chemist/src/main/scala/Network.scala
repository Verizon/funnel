package funnel
package chemist

import scalaz.concurrent.Task
import scalaz.stream.{Process, Sink}
import scalaz.syntax.apply._
import journal.Logger

trait RemoteFlask {
  val commands: Sink[Task,FlaskCommand]
}

class HttpFlask(http: dispatch.Http) extends RemoteFlask {
  import FlaskCommand._

  private lazy val log = Logger[HttpFlask]

  val commands: Sink[Task,FlaskCommand] = Process.constant {
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
    val payload: Map[ClusterName, List[SafeURL]] =
      targets.groupBy(_.cluster).mapValues(_.map(_.url).toList)

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
    val payload: Map[ClusterName, List[SafeURL]] =
      targets.groupBy(_.cluster).mapValues(_.map(_.url).toList)

    val uri = to.asURI(path = "discard")

    val req = Task.delay(url(uri.toString) << payload.toList.asJson.nospaces) <* Task.delay(log.debug(s"submitting to $uri: $payload"))
    req.flatMap(r => fromScalaFuture(http(r OK as.String)))
  }

}
