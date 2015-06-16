package funnel
package chemist

import scalaz.{\/,-\/,\/-}
import java.net.URI
import org.http4s.Uri
import org.http4s.Uri._
import org.http4s.util.CaseInsensitiveString

case class Location(
  host: String,
  port: Int,
  datacenter: String,
  protocol: String = "http",
  isPrivateNetwork: Boolean = false
) {
  def asJavaURI(path: String = ""): URI = new URI(protocol, null, host, port, s"/$path", null, null)
  def asUri(path: String = ""): Uri =
    Uri(scheme    = Some(CaseInsensitiveString(protocol)),
        authority = Some(Authority(host = RegName(host),
                                   port = Some(port))),
        path      = s"/$path")
}

object Location {
  def localhost: Location =
    Location(
      host = "127.0.0.1",
      port = 5775,
      datacenter = "local",
      protocol = "http")

  def telemetryLocalhost: Location =
    Location(
      host = "127.0.0.1",
      port = 7390,
      datacenter = "local",
      protocol = "tcp")
}
