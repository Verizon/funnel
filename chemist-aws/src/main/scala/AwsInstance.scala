package funnel
package chemist
package aws

import java.net.URI
import scalaz.{\/,\/-,-\/}

case class AwsInstance(
  id: String,
  location: Location,
  telemetryLocation: Option[Location] = None,
  tags: Map[String,String] = Map.empty
){
  def application: Option[Application] = {
    for {
      b <- tags.get("funnel:target:name") orElse tags.get("type") orElse tags.get("Name")
      c <- tags.get("funnel:target:version") orElse tags.get("revision") orElse(Some("unknown"))
      d  = tags.get("aws:cloudformation:stack-name")
            .flatMap(_.split('-').lastOption.find(_.length > 3))
    } yield Application(
      name = b,
      version = c,
      qualifier = tags.get("funnel:target:qualifier") orElse d
    )
  }

  def asURI: URI = location.asURI()

  def targets: Set[Target] =
    (for {
       a <- application
     } yield Target.defaultResources.map(r =>
        Target(a.toString, location.asURI(r), location.isPrivateNetwork))
    ).getOrElse(Set.empty[Target])
}
