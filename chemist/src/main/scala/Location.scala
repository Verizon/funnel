package funnel
package chemist

import scalaz.{\/,-\/,\/-}
import java.net.URL

case class Location(
  dns: Option[String],
  ip: String = "", // currently not needed so skipping for speed
  port: Int = 5775,
  datacenter: String,
  isPrivateNetwork: Boolean = false
){ self =>
  def asURL(port: Int = port, path: String = ""): Throwable \/ URL =
    dns match {
      case Some(h) if h.nonEmpty => \/-(new URL(s"http://$h:$port/$path"))
      case _ => -\/(InvalidLocationException(self))
    }
}
object Location {
  def localhost: Location =
    Location(
      dns = Some("localhost"),
      ip = "127.0.0.1",
      port = 5775,
      datacenter = "local")
}
