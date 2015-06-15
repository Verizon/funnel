package funnel
package chemist

import scalaz.{\/,-\/,\/-}
import java.net.URI
import LocationIntent._

case class Location(
  host: String,
  port: Int,
  datacenter: String,
  protocol: NetworkScheme = NetworkScheme.Http,
  isPrivateNetwork: Boolean = false,
  intent: LocationIntent
){
  def asURI(path: String = ""): URI =
    URI.create(s"${protocol.toString}://$host:$port/$path")
}

object Location {
  def fromURI(uri: URI, dc: String, int: LocationIntent): Option[Location] =
    for {
      a <- Option(uri.getHost)
      b <- Option(uri.getPort)
      c <- NetworkScheme.fromString(uri.getScheme)
    } yield Location(
      host = a,
      port = b,
      protocol = c,
      datacenter = dc,
      intent = int)
}
