package funnel
package chemist

import scalaz.Order
import scalaz.std.tuple._
import scalaz.std.string._
import java.net.URI

case class TargetID(value: String) extends AnyVal

/**
 * A target is a physical thing we would like to monitor in the
 * system. It must have a URI representing its location.
 */
case class Target(
  /* cluster represents the phyiscal deployment. It must be unique
     even if the same software is already deployed (e.g. A-B testing) */
  cluster: ClusterName,
  /* the location this target can be reached. follow the RFC spec for
     guidence on how to properly encode schemes and paths. */
  uri: URI
)

object Target {
  val defaultResources = Set("stream/previous")

  implicit val orderTarget: Order[Target] =
    Order[(String,String)].contramap(t => (t.cluster, t.uri.toString))
}
