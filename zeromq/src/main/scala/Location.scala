package oncue.svc.funnel
package zeromq

import scalaz.concurrent.Task
import scalaz.\/
import java.net.URI

case class Location(
  uri: URI,
  protocol: Protocol,
  hostOrPath: String
)
object Location {

  private def hostOrPath(uri: URI): Option[String] = {
    val port = Option(uri.getPort).flatMap {
      case -1 => Option.empty[String]
      case o  => Option(o.toString)
    }
    Option(uri.getHost).flatMap(h => port.map(h + ":" + _)
      ) orElse Option(uri.getPath)
  }

  private def go[A](o: Option[A])(errMsg: String): Throwable \/ A =
    \/.fromEither(o.toRight(new RuntimeException(errMsg)))

  def apply(uri: URI): Throwable \/ Location =
    for {
      a <- go(Protocol.fromString(uri.getScheme)
            )("Unable to infer protocol scheme from URI.")
      b <- go(hostOrPath(uri))("URI contained no discernible host:port or path.")
    } yield Location(uri, a, b)
}
