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
    println(tags)

    for {
      b <- tags.get("type").orElse(tags.get("Name"))
      c <- tags.get("revision").orElse(Some("unknown"))
    } yield Application(
      name = b,
      version = c,
      qualifier = tags.get("aws:cloudformation:stack-name")
        .flatMap(_.split('-').lastOption.find(_.length > 3)))
  }

  def asURL: Throwable \/ URL = location.asURL()
}
