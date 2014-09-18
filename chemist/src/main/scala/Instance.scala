package oncue.svc.funnel.chemist

import java.net.URL
import scalaz.{\/,\/-,-\/}

case class Location(
  dns: Option[String],
  ip: String = "", // currently not needed so skipping for speed
  port: Int = 5775,
  datacenter: String,
  isPrivateNetwork: Boolean = false
){
  def asURL(port: Int = port, path: String = ""): Throwable \/ URL =
    dns match {
      case Some(h) if h.nonEmpty => \/-(new URL(s"http://$h:$port/$path"))
      case _ => -\/(new RuntimeException(s"No hostname is specified for the specified location."))
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

case class Application(
  name: String,
  version: String
)

case class Instance(
  id: String,
  location: Location = Location.localhost,
  firewalls: Seq[String], // essentially security groups
  tags: Map[String,String] = Map.empty
){
  def application: Option[Application] =
    for {
      a <- tags.get("AppName")
      b <- tags.get("revision")
    } yield Application(a,b)

  def asURL: Throwable \/ URL = location.asURL()
}
