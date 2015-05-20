package funnel
package chemist

import scalaz.Order
import scalaz.std.tuple._
import scalaz.std.string._
import java.net.URI

case class TargetID(value: String) extends AnyVal

case class Target(cluster: ClusterName, uri: URI, isPrivateNetwork: Boolean)

object Target {
  val defaultResources = Set("stream/previous")

  implicit val orderTarget: Order[Target] = Order[(String,String)].contramap(t => (t.cluster, t.uri.toString))
}
