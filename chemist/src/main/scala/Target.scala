package funnel
package chemist

import scalaz.Order
import scalaz.std.tuple._
import scalaz.std.string._

case class Target(cluster: ClusterName, url: SafeURL)

  object Target {
    val defaultResources = Seq("stream/previous")

    implicit val orderTarget: Order[Target] = Order[(String,String)].contramap(t => (t.cluster, t.url.underlying))

    def fromInstance(resources: Seq[String] = defaultResources)(i: Instance): Set[Target] =
      (for {
        a <- i.application
        b = i.asURI
      } yield resources.map(r => Target(a.toString, SafeURL(b+r))).toSet
      ).getOrElse(Set.empty[Target])

}
