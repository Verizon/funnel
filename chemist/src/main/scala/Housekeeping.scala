package funnel
package chemist

import scalaz.concurrent.Task
import scalaz.std.string._
import scalaz.syntax.apply._
import journal.Logger
import java.net.URI

object Housekeeping {
  import Sharding._
  private lazy val log = Logger[Housekeeping.type]

  /**
   * Given a collection of flask instances, find out what exactly they are already
   * mirroring and absorb that into the view of the world.
   *
   * This function should only really be used startup of chemist.
   */
  def gatherAssignedTargets(flasks: Seq[Flask])(http: dispatch.Http): Task[Distribution] =
    (for {
       a <- Task.gatherUnordered(flasks.map(
            f => requestAssignedTargets(f.location)(http).map(f -> _)))
    } yield a.foldLeft(Distribution.empty){ (a,b) =>
      a.alter(b._1.id, o => o.map(_ ++ b._2) orElse Some(Set.empty[Target]) )
    }) or Task.now(Distribution.empty)

  import funnel.http.{Cluster,JSON => HJSON}

  /**
   * Call out to the specific location and grab the list of things the flask
   * is already mirroring.
   */
  private def requestAssignedTargets(location: Location)(http: dispatch.Http): Task[Set[Target]] = {
    import argonaut._, Argonaut._, JSON._, HJSON._
    import dispatch._, Defaults._

    val a = location.uriFromTemplate(LocationTemplate(s"http://@host:@port/mirror/sources"))

    val req = Task.delay(url(a.toString)) <* Task.delay(log.debug(s"requesting assigned targets from $a"))
    req flatMap { b =>
      fromScalaFuture(http(b OK as.String)).map { c =>
        Parse.decodeOption[List[Cluster]](c
        ).toList.flatMap(identity
        ).foldLeft(Set.empty[Target]){ (a,b) =>
          b.urls.map(s => Target(b.label, new URI(s), false)).toSet
        }
      }
    }
  }
}
