package funnel
package chemist

import java.net.URI
import scalaz.{\/,-\/,\/-,Nondeterminism}
import scalaz.concurrent.{Task,Strategy}
import scalaz.stream.{Process, time}
import scalaz.std.string._
import scalaz.std.set._
import scalaz.syntax.apply._
import scalaz.syntax.kleisli._
import scalaz.syntax.traverse._
import scalaz.syntax.id._
import scalaz.std.vector._
import scalaz.std.option._
import journal.Logger
import metrics._

object Housekeeping {
  import Chemist.contact
  import Sharding._
  import concurrent.duration._

  private lazy val log = Logger[Housekeeping.type]
  private lazy val defaultPool = Strategy.Executor(Chemist.defaultPool)

  /**
   * Given a collection of flask instances, find out what exactly they are already
   * mirroring and absorb that into the view of the world.
   *
   * This function should only really be used startup of chemist.
   */
  def gatherAssignedTargets(flasks: Seq[Flask])(http: dispatch.Http): Task[Distribution] = {
    val d = (for {
       a <- Nondeterminism[Task].gatherUnordered(flasks.map(
         f => requestAssignedTargets(f.location)(http).map(f -> _).flatMap { t =>
           Task.delay {
             log.debug(s"Read targets $t from flask $f")
             t
           }
         }
       ))
    } yield a.foldLeft(Distribution.empty){ (a,b) =>
      a.updateAppend(b._1, b._2)
    }).map { dis =>
      log.debug(s"Gathered distribution $dis")
      dis
    }

    GatherAssignedLatency.timeTask(d) or Task.now(Distribution.empty)
  }

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
      http(b OK as.String).map { c =>
        Parse.decodeOption[List[Cluster]](c
        ).toList.flatMap(identity
        ).map { cluster =>
          log.debug(s"Received cluster $cluster from $a")
          cluster
        }.foldLeft(Set.empty[Target]){ (a,b) =>
          b.urls.map(s => Target(b.label, new URI(s))).toSet
        }
      }
    }
  }
}
