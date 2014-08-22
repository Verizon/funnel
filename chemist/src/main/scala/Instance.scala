package oncue.svc.funnel.chemist

case class Location(
  dns: Option[String],
  ip: String = "", // currently not needed so skipping for speed
  datacenter: String,
  isPrivateNetwork: Boolean = false
)

case class Application(
  name: String,
  version: String
)

import java.net.URL
import scalaz.{\/,\/-,-\/}

case class Instance(
  id: String,
  location: Location,
  firewalls: Seq[String], // essentially security groups
  tags: Map[String,String] = Map.empty
){
  def application: Option[Application] =
    for {
      a <- tags.get("AppName")
      b <- tags.get("revision")
    } yield Application(a,b)

  def host: Option[Host] =
    location.dns

  def asURL: Throwable \/ URL = asURL()

  def asURL(port: Int = 5775, path: String = ""): Throwable \/ URL =
    location.dns match {
      case Some(h) if h.nonEmpty => \/-(new URL(s"http://$h:$port/$path"))
      case _ => -\/(new RuntimeException(s"No hostname is specified for $id"))
    }

}
