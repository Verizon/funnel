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
  private def fromAppNameAndRevision: Option[Application] =
    for {
      a <- tags.get("AppName")
      b <- tags.get("revision")
    } yield Application(a,b)

  private def fromLegacyStackName: Option[Application] =
    tags.get("aws:cloudformation:stack-name").map(Application(_, "unknown"))

  def application: Option[Application] =
    fromAppNameAndRevision orElse fromLegacyStackName

  def asURL: Throwable \/ URL = location.asURL()
}
