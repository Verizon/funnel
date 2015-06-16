package funnel
package chemist
package aws

import java.net.URI
import scalaz.{\/,\/-,-\/}

case class AwsInstance(
  id: String,
  location: Location = Location.localhost,
  telemetryLocation: Location = Location.telemetryLocalhost,
  firewalls: Seq[String], // essentially security groups
  tags: Map[String,String] = Map.empty
) { // extends Instance {
  def application: Option[Application] = {
    for {
      b <- tags.get("type").orElse(tags.get("Name"))
      c <- tags.get("revision").orElse(Some("unknown"))
    } yield Application(
      name = b,
      version = c,
      qualifier = tags.get("aws:cloudformation:stack-name")
        .flatMap(_.split('-').lastOption.find(_.length > 3)))
  }

  def asURI: URI = location.asJavaURI()

  def targets: Set[Target] =
    (for {
       a <- application
     } yield Target.defaultResources.map(r => Target(a.toString, location.asJavaURI(r), location.isPrivateNetwork))
    ).getOrElse(Set.empty[Target])

  def asFlask: Flask = Flask(FlaskID(id), location, telemetryLocation)
}
