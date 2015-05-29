package funnel
package zeromq

import argonaut.EncodeJson
import scalaz.stream.async.mutable.Signal
import scalaz.stream.Process
import scalaz.concurrent.Task
import scalaz.{\/,-\/,\/-}
import java.net.URI

object Publish {
  import http.JSON._
  import ZeroMQ.{link,log,write}
  import scalaz.stream.async.signalOf

  val S = scalaz.concurrent.Strategy.Executor(Monitoring.defaultPool)
  private[zeromq] val UTF8 = java.nio.charset.Charset.forName("UTF-8")
  private[zeromq] val alive: Signal[Boolean] = signalOf[Boolean](true)(S)
  val defaultUnixSocket = "/var/run/funnel.socket"
  val defaultTcpSocket  = "127.0.0.1:7390"

  /////////////////////////////// USAGE ///////////////////////////////////

  // unsafe!
  def to(endpoint: Endpoint)(signal: Signal[Boolean], instance: Monitoring): Unit =
    link(endpoint)(signal)(socket =>
      fromMonitoring(instance)(m => log.debug(m))
        .through(write(socket))
        .onComplete(Process.eval(stop(signal)))
    ).run.runAsync(_ match {
      case -\/(err) =>
        log.error(s"Unable to stream monitoring events to the socket ${endpoint.location.uri}")
        log.error(s"Error was: $err")

      case \/-(win) =>
        log.info(s"Streaming monitoring datapoints to the socket at ${endpoint.location.uri}")
    })

  import sockets._

  private def use(e: Throwable \/ Endpoint)(s: Signal[Boolean])(m: Monitoring): Unit =
    if(Ø.isEnabled){
      e match {
        case \/-(e) => to(e)(s, m)
        case -\/(f) => sys.error(s"Unable to create endpoint; the specified URI is likley malformed: $f")
      }
    } else Ø.log.warn("ZeroMQ binaries not installed. No Funnel telemetry will be published.")

  def toTcpSocket(
    host: String = defaultTcpSocket,
    signal: Signal[Boolean] = alive,
    instance: Monitoring = Monitoring.default
  ): Unit = use(Endpoint(push &&& connect, new URI(s"tcp://$host")))(signal)(instance)

  def toUnixSocket(
    path: String = defaultUnixSocket,
    signal: Signal[Boolean] = alive,
    instance: Monitoring = Monitoring.default
  ): Unit = use(Endpoint(push &&& connect, new URI(s"ipc://$path")))(signal)(instance)

  /////////////////////////////// INTERNALS ///////////////////////////////////

  // TODO: implement binary serialisation here rather than using the JSON from `http` module
  private def dataEncode[A](a: A)(implicit A: EncodeJson[A]): String =
    A(a).nospaces

  def fromMonitoring(M: Monitoring)(implicit log: String => Unit): Process[Task, Datapoint[Any]] =
    Monitoring.subscribe(M)(Key.StartsWith("previous"))


  implicit val transportDatapoint: Transportable[Datapoint[Any]] = Transportable { d =>
    val window = d.key.name.takeWhile(_ != '/')
    new Transported(Schemes.fsm,
                Versions.v1,
                Windows.fromString(window),
                Some(Topic(d.key.attributes.get("kind").getOrElse("unknown"))),
                    s"${dataEncode(d)(EncodeDatapoint[Any])}\n".getBytes(UTF8))
  }
}
