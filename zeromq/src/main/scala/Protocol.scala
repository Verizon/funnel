package oncue.svc.funnel
package zeromq

abstract class Protocol(name: String){
  override def toString: String = name
}
object Protocol {
  lazy val all: Seq[Protocol] =
    TCP :: IPC :: UDP :: InProc :: Pair :: Nil

  def fromString(s: String): Option[Protocol] =
    all.find(_.toString == s.toLowerCase)
}

case object TCP extends Protocol("tcp")
case object IPC extends Protocol("ipc")
case object UDP extends Protocol("udp")
case object InProc extends Protocol("proc")
case object Pair extends Protocol("pair")

import scalaz.concurrent.Task
import org.zeromq.ZMQ, ZMQ.Socket
import scalaz.\/
import java.net.URI

case class Location(uri: URI){
  val protocol: Option[Protocol] = Protocol.fromString(uri.getScheme)
  val hostOrPath: Option[String] = {
    val port = Option(uri.getPort).flatMap {
      case -1 => Option.empty[String]
      case o  => Option(o.toString)
    }
    Option(uri.getHost).flatMap(h => port.map(h + ":" + _)
      ) orElse Option(uri.getPath)
  }

  assert(protocol.isDefined, "Unable to infer protocol scheme from URI.")
  assert(hostOrPath.isDefined, "URI contained no discernible host or path.")
}

case class Endpoint(
  mode: Mode,
  location: Location){
  def configure(s: Socket): Task[Unit] =
    mode.configure(location, s)
}
object Endpoint {
  def apply(m: Mode, u: URI): Throwable \/ Endpoint = {
    \/.fromTryCatchNonFatal(Endpoint(m, Location(u)))
  }
}
