package funnel
package chemist
package aws

import java.net.URI
import scalaz.{\/,\/-,-\/,NonEmptyList}

/**
 * Represents an EC2 machine in AWS. Every single machine in AWS should
 * be monitorable, or at least have a monitorable location, even if it
 * is not ultimatly reachable.
 *
 * Tags are used heavily to datamine what kind of intstance we're looking
 * at, during various parts of the process. Your instances need the following
 * tags set on the instance (as a minimum):
 *
 * - `funnel:target:name`: e.g. myapp
 * - `funnel:target:version`: e.g. 1.2.3
 * - `funnel:target:qualifier`: e.g. XdfGeq4 (uniqley identify this deployment)
 * - `funnel:mirror:uri-template`: e.g. http://@host:5775; lets chemist
 *                                 know where to connect to for mirroring
 *
 * Do be aware that EC2 has a 10 tag limit (so dumb!)
 */
case class AwsInstance(
  id: String,
  tags: Map[String,String] = Map.empty,
  locations: NonEmptyList[Location]
){
  def location: Location =
    locations.head

  def supervision: Option[Location] =
    locations.toZipper.findZ(_.intent == LocationIntent.Supervision).map(_.focus)

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
