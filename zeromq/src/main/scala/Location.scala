package funnel
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
      a <- go(uri.getScheme.split('+').lastOption
            )(s"URI did not have the correct 'zeromq+' scheme prefix. uri = '$uri'")
      b <- go(Protocol.fromString(a)
            )(s"Unable to infer protocol scheme from URI '$uri'")
      c <- go(hostOrPath(uri)
            )("URI contained no discernible host:port or path.")
    } yield Location(URI.create(s"$a:${uri.getSchemeSpecificPart}"), b, c)
}
