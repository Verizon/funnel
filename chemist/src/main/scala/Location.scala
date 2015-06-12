package funnel
package chemist

import scalaz.{\/,-\/,\/-}
import java.net.URI
import LocationIntent._

case class Location(
  host: String,
  port: Int,
  datacenter: String,
  protocol: String = "http",
  isPrivateNetwork: Boolean = false,
  intent: LocationIntent
){
  def asURI(path: String = ""): URI =
    new URI(protocol, null, host, port, s"/$path", null, null)
}

object Location {
  def fromURI(uri: URI, dc: String, int: LocationIntent): Option[Location] =
    for {
      a <- Option(uri.getHost)
      b <- Option(uri.getPort)
      c <- Option(uri.getScheme)
    } yield Location(
      host = a,
      port = b,
      protocol = c,
      datacenter = dc,
      intent = int)
}
