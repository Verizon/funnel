//: ----------------------------------------------------------------------------
//: Copyright (C) 2015 Verizon.  All Rights Reserved.
//:
//:   Licensed under the Apache License, Version 2.0 (the "License");
//:   you may not use this file except in compliance with the License.
//:   You may obtain a copy of the License at
//:
//:       http://www.apache.org/licenses/LICENSE-2.0
//:
//:   Unless required by applicable law or agreed to in writing, software
//:   distributed under the License is distributed on an "AS IS" BASIS,
//:   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//:   See the License for the specific language governing permissions and
//:   limitations under the License.
//:
//: ----------------------------------------------------------------------------
package funnel
package chemist

import scalaz.Order
import scalaz.std.string._
import scalaz.std.vector._
import scalaz.syntax.monadPlus._

case class FlaskID(value: String) extends AnyVal

object FlaskID {
  implicit val flaskIdOrder: Order[FlaskID] = implicitly[Order[String]].contramap[FlaskID](_.value)
}

case class Flask(id: FlaskID, location: Location)

import scalaz.Nondeterminism
import scalaz.concurrent.Task
import journal.Logger
import java.net.URI

object Flask {
  import FlaskID._
  import metrics._
  import scalaz.std.set._
  import scalaz.syntax.apply._
  import Sharding.Distribution

  private[this] val log = Logger[Flask.type]

  implicit val flaskOrder: Order[Flask] = implicitly[Order[FlaskID]].contramap[Flask](_.id)

  /**
   * Given a collection of flask instances, find out what exactly they are already
   * mirroring and absorb that into the view of the world.
   *
   * If some flasks are down then they will not be returned as part of distribution
   * and should not be allocated work too. This might lead to double work assignment in case of
   * network partitioning but Chemist need to be able to correct double assignment issues anyways.
   */
  def gatherAssignedTargets(flasks: Seq[Flask])(http: dispatch.HttpExecutor): Task[Distribution] = {
    val lookups = Nondeterminism[Task].gatherUnordered(
      flasks.map(
        f => requestAssignedTargets(f.location)(http).map(f -> _).attempt.map {
          v => v.leftMap(f -> _)
        }
      )
    )

    val d = lookups.map {
      all =>
        val (errors, success) = all.toVector.separate

        metrics.deadFlasks.set(errors.size)
        metrics.liveFlasks.set(success.size)

        errors.foreach {
          e => log.error(s"[gatherAssigned] dead flask=${e._1}, problem=${e._2}")
            print(s"[gatherAssigned] dead flask=${e._1}, problem=${e._2}\n")
        }

        val dis = success.foldLeft(Distribution.empty)(
          (a,b) => a.updateAppend(b._1, b._2)
        )

        val cnt = dis.values.map(_.size).sum
        metrics.knownSources.set(cnt)

        log.info(s"[gatherAssigned] distribution stats: flasks=${dis.keySet.size} targets=$cnt")
        log.debug(s"[gatherAssigned] distribution details: $dis")

        dis
    }

    GatherAssignedLatency.timeTask(d) or Task.now(Distribution.empty)
  }

  import funnel.http.{Cluster,JSON => HJSON}

  /**
   * Call out to the specific location and grab the list of things the flask
   * is already mirroring.
   */
  private def requestAssignedTargets(location: Location)(http: dispatch.HttpExecutor): Task[Set[Target]] = {
    import argonaut._, Argonaut._, JSON._, HJSON._
    import dispatch._, Defaults._

    val a = location.uriFromTemplate(LocationTemplate(s"http://@host:@port/mirror/sources"))
    val req = Task.delay(url(a.toString)) <* Task.delay(log.debug(s"requesting assigned targets from $a"))
    req flatMap {
      b => http.apply(b OK as.String).map {
        c => Parse.decodeOption[List[Cluster]](c).toList.flatten.foldLeft(Set.empty[Target]){
          (a,b) => a ++ b.urls.map(s => Target(b.label, new URI(s))).toSet
        }
      }
    }
  }
}
