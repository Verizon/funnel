package oncue.svc.funnel
package zeromq

import scalaz.concurrent.Task
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