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
package elastic

import java.net.URI
import scalaz.concurrent.Task
import scala.concurrent.duration.Duration
import scalaz.stream._
import java.util.{Date,TimeZone}
import java.text.SimpleDateFormat
import scalaz.concurrent.Strategy.Executor

/**
 * This class provides a consumer for the funnel monitoring stream that takes
 * the events emitted and constructs a JSON document that will be sent to elastic
 * search. Each and every metric key has its own individual document, where fields
 * are flattened into a simplistic structure, which is optimal for the manner in
 * which elastic search stores documents and manages in-memory indexing with Lucene.
 *
 * {
 *   "@timestamp": "2015-09-23T17:31:38.965+0000",
 *   "window": "previous",
 *   "name": "system.tcp.curr_estab",
 *   "host": "ip-10-124-30-93.ec2.internal",
 *   "uri": "http://ip-10-124-30-93.ec2.internal:5775/stream/previous",
 *   "cluster": "gong-1.1.85-zqhpLpQ",
 *   "units": "unknown",
 *   "environment": "dev",
 *   "stack": "gong-1.1.85-zqhpLpQ",
 *   "kind": "numeric",
 *   "type": "unknown",
 *   "numeric": {
 *     "count": 6,
 *     "variance": 0.24999999999999992,
 *     "kurtosis": -2,
 *     "mean": 10.5,
 *     "last": 11,
 *     "skewness": -7.401486830834381E-17,
 *     "standardDeviation": 0.4999999999999999
 *   }
 * }
 */
case class ElasticFlattened(M: Monitoring, H: HttpLayer = SharedHttpLayer.H){
  import Elastic._
  import argonaut._, Argonaut._
  import Process._
  import http.JSON._

  /**
   *
   */
  implicit class DateAsString(d: Date){
    private val sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZZ")

    def inUtc: String =
      inTimezone(TimeZone.getTimeZone("UTC"))

    def inTimezone(tz: TimeZone): String = {
      sdf.setTimeZone(tz)
      sdf.format(d)
    }
  }

  /**
   *
   */
  private[elastic] def toJson[A](
    environment: String,
    flaskNameOrHost: String,
    flaskCluster: String
  )(pt: Datapoint[A]): Json = {
    val keyname = pt.key.name
    val (window,name) = keyname.split('/') match {
      case Array(h,t@_*) => (h,t.mkString("."))
      case _ => ("unknown", keyname) // should not happen; what would it mean?
    }
    val attrs = pt.key.attributes
    val source: String = attrs.getOrElse(AttributeKeys.source, flaskNameOrHost)
    val host: String = attrs.get(AttributeKeys.source
      ).map(u => new URI(u).getHost).getOrElse(flaskNameOrHost)
    val experimentId: Option[String] = attrs.get(AttributeKeys.experimentID)
    val experimentGroup: Option[String] = attrs.get(AttributeKeys.experimentGroup)
    val kind: Option[String] = attrs.get(AttributeKeys.kind).map(_.toLowerCase)
    val cluster: String = attrs.getOrElse(AttributeKeys.cluster, flaskCluster)
    val units: String = pt.units.toString.toLowerCase
    val edge: Option[String] = attrs.get(AttributeKeys.edge)
    val keytype: String = pt.typeOf.description.toLowerCase
    val partialJson: Json =
      ("environment"      := environment) ->:
      ("flask"            := flaskNameOrHost) ->:
      ("stack"            := cluster) ->:
      ("cluster"          := cluster) ->:
      ("uri"              := source) ->:
      ("host"             := host) ->:
      ("window"           := window) ->:
      ("units"            := units) ->:
      ("type"             := keytype) ->:
      ("name"             :=? Decode.utf8(name)) ->?:
      ("kind"             :=? kind) ->?:
      ("edge"             :=? edge) ->?:
      ("experiment_id"    :=? experimentId) ->?:
      ("experiment_group" :=? experimentGroup) ->?:
      ("@timestamp"       := (new Date).inUtc) ->: jEmptyObject

    /**
     * alright, this is horrible. runar will hate me, sorry, but this is a bit of
     * future-proofing just to cover the cases where we sometime later add a gauge
     * that does not fit into our existing scheme, and the field mappings in ES
     * will not be completely broken. Discussed this at length with ops, and we felt
     * this was the best path right now. (*sigh*)
     *
     * likewise, the hack here with elapsed, remaining and uptime is because they
     * are currently incorrectly reporting themselves as kind=timer, which then
     * screws up the mapping because we expect timers to be a `Stats` instance. To
     * workaround this bug, i've added this hack until we can migrate users to a
     * newer version of funnel core in the leaves of our system.
     */
    partialJson.deepmerge {
      kind.map { k =>
        val value = (pt.asJson -| "value").get
        if(k == "gauge")
          Json(s"${kind.get}-$keytype" -> value)
        else if(name == "elapsed" || name == "remaining" || name == "uptime")
          Json(name -> value)
        else
          Json(k -> value)
      }.getOrElse(jEmptyObject)
    }
  }

  /**
   * Returns a stream that takes a datapoint and renders it as Json
   */
  def render[A](
    environment: String,
    flaskNameOrHost: String,
    flaskCluster: String
  ): Process1[Option[Datapoint[A]], Json] = {
    await1[Option[Datapoint[A]]]
     .map(_.toSeq.map(toJson(environment, flaskNameOrHost, flaskCluster)))
     .flatMap(emitAll)
     .repeat
  }

  /**
   * Merges datapoints and regularly timed ticks, then renders them as JSON.
   */
  def publish(
    environment: String,
    flaskNameOrHost: String,
    flaskCluster: String
  ): ES[Unit] = {
    //TODO: consider dedicated pool for ES publishing, need to guarantee we will not starve when mirroring many agents
    val E = Executor(Monitoring.defaultPool)
    bufferAndPublish(flaskNameOrHost, flaskCluster)(M, E, H){ cfg =>

      val data: Process[Task, Option[Datapoint[Any]]] =
        Monitoring.
          subscribe(M){ k => cfg.groups.exists(g => k.startsWith(g)) }.
          map(Option.apply)

      val ticks: Process[Task, Duration] = time.awakeEvery(cfg.subscriptionTimeout)(E, Monitoring.schedulingPool)
      val ticksAsEmptyDatapoints: Process[Task, Option[Datapoint[Any]]] = ticks.map(_ => Option.empty[Datapoint[Any]])
      val mergeTicksAndData: Process[Task, Option[Datapoint[Any]]] = ticksAsEmptyDatapoints.wye(data)(wye.merge)(E)

      mergeTicksAndData |> render(environment, flaskNameOrHost, flaskCluster)
    }
  }

}
