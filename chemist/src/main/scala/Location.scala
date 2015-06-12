package funnel
package chemist

import scalaz.{\/,-\/,\/-}
import java.net.URI

case class Location(
  host: String,
  port: Int,
  datacenter: String,
  protocol: String = "http",
  isPrivateNetwork: Boolean = false
) {
  def asURI(path: String = ""): URI =
    new URI(protocol, null, host, port, s"/$path", null, null)
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
