package funnel
package chemist

import java.net.URL
import scalaz.{\/,\/-,-\/}

case class Instance(
  id: String,
  location: Location = Location.localhost,
  firewalls: Seq[String], // essentially security groups
  tags: Map[String,String] = Map.empty
){
  def application: Option[Application] = {
    for {
      a <- tags.get("aws:cloudformation:stack-name").flatMap(_.split('-').lastOption
             ).orElse(Option(java.util.UUID.randomUUID.toString))
      b <- tags.get("type")
      c <- tags.get("revision")
    } yield Application(b,c,a)
  }

  def asURL: Throwable \/ URL = location.asURL()
}
