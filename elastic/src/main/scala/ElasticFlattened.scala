package funnel
package elastic

import java.net.URI
import scalaz.stream._
import concurrent.duration._
import java.util.{Date,TimeZone}
import java.text.SimpleDateFormat
import scalaz.concurrent.Strategy.Executor

/**
 * This class provides a consumer for the funnel montioring stream that takes
 * the events emitted and constructs a JSON document that will be sent to elastic
 * search. Each and every metric key has its own individual document, where fields
 * are flattened into a simplistic structure, which is optiomal for the manner in
 * which elastic search stores documents and manages in-memory indexing with Lucene.
 *
 * {
 *   "environment": "imdev",
 *   "host": "ip-10-124-10-111",
 *   "window": "previous",
 *   "stats": {
 *     "count": 2,
 *     "skewness": 0,
 *     "last": 4330.647608,
 *     "standardDeviation": 305.13561600000025,
 *     "variance": 93107.74415169962,
 *     "kurtosis": -2,
 *     "mean": 4025.5119919999997
 *   },
 *   "name": "jvm.memory.total.used",
 *   "units": "Megabytes",
 *   "@timestamp": "2015-09-21T21:06:42.000Z",
 *   "stack": "flask-4.1.405",
 *   "kind": "numeric",
 *   "uri": "7557/stream/previous"
 * }
 */
case class ElasticFlattened(M: Monitoring){
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
    val name = pt.key.name
    val attrs = pt.key.attributes
    val source: String = attrs.get(AttributeKeys.source).getOrElse(flaskNameOrHost)
    val host: String = attrs.get(AttributeKeys.source
      ).map(u => (new URI(u)).getHost).getOrElse(flaskNameOrHost)
    val experimentId: Option[String] = attrs.get(AttributeKeys.experimentID)
    val experimentGroup: Option[String] = attrs.get(AttributeKeys.experimentGroup)
    val kind: Option[String] = attrs.get(AttributeKeys.kind).map(_.toLowerCase)
    val cluster: String = attrs.get(AttributeKeys.cluster).getOrElse(flaskCluster)
    val units: String = attrs.get(AttributeKeys.units).map(_.toLowerCase).getOrElse("unknown")
    val edge: Option[String] = attrs.get(AttributeKeys.edge)
    val partialJson: Json =
      ("environment"      := environment) ->:
      ("stack"            := cluster) ->:
      ("cluster"          := cluster) ->:
      ("uri"              := source) ->:
      ("host"             := host) ->:
      ("units"            := units) ->:
      ("name"             := name) ->:
      ("kind"             :=? kind) ->?:
      ("edge"             :=? edge) ->?:
      ("experiment_id"    :=? experimentId) ->?:
      ("experiment_group" :=? experimentGroup) ->?:
      ("@timestamp"       := (new Date).inUtc) ->: jEmptyObject

    partialJson.deepmerge(Json(kind.get -> (pt.asJson -| "value").get))
  }

  /**
   *
   */
  def render[A](
    environment: String,
    flaskNameOrHost: String,
    flaskCluster: String
  ): Process1[Option[Datapoint[A]], Json] =
    await1[Option[Datapoint[A]]].flatMap { o =>
      emitAll(o.toSeq.map(pt =>
        toJson(environment, flaskNameOrHost, flaskCluster)(pt)))
    }.repeat

  /**
   *
   */
  def publish(
    environment: String,
    flaskNameOrHost: String,
    flaskCluster: String
  ): ES[Unit] = {
    val E = Executor(Monitoring.defaultPool)
    bufferAndPublish(flaskNameOrHost, flaskCluster)(M, E){ cfg =>
      val subscription = Monitoring.subscribe(M){ k =>
        cfg.groups.exists(g => k.startsWith(g))}.map(Option.apply)

      time.awakeEvery(cfg.subscriptionTimeout)(E,
        Monitoring.schedulingPool).map(_ => Option.empty[Datapoint[Any]]
      ).wye(subscription)(wye.merge)(E) |>
        render(environment, flaskNameOrHost, flaskCluster)
    }
  }

}