package funnel
package elastic

import java.net.URI
import scalaz.stream._
import java.util.{Date,TimeZone}
import java.text.SimpleDateFormat

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

  implicit class DateAsString(d: Date){
    private val sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZZ")

    def inUtc: String =
      inTimezone(TimeZone.getTimeZone("UTC"))

    def inTimezone(tz: TimeZone): String = {
      sdf.setTimeZone(tz)
      sdf.format(d)
    }
  }

  def foooooo[A](environment: String, flaskName: String, flaskCluster: String): Process1[Option[Datapoint[A]], Json] =
    await1[Option[Datapoint[A]]].flatMap { o =>
      emitAll(o.toSeq.map { pt =>
        val name = pt.key.name
        val attrs = pt.key.attributes
        val source: String = attrs.get(AttributeKeys.source
          ).map(u => (new URI(u)).getHost).getOrElse(flaskName)
        val experimentId: Option[String] = attrs.get(AttributeKeys.experimentID)
        val experimentGroup: Option[String] = attrs.get(AttributeKeys.experimentGroup)
        val kind: Option[String] = attrs.get(AttributeKeys.kind)
        val cluster: String = attrs.get(AttributeKeys.cluster).getOrElse(flaskCluster)
        ("environment"      := environment) ->:
        ("stack"            := cluster) ->:
        ("cluster"          := cluster) ->:
        ("uri"              := "uri we were monitoring") ->:
        ("host"             := "host from the uri") ->:
        ("experiment_id"    :=? experimentId) ->?:
        ("experiment_group" :=? experimentGroup) ->?:
        ("@timestamp"       := (new Date).inUtc) ->: jEmptyObject
      })
    }.repeat

  // def publish(flaskName: String, flaskCluster: String): ES[Unit] = {

  // }
}