package funnel
package elastic

import scala.concurrent.duration._

case class ElasticCfg(
  url: String,                         // Elastic URL
  indexName: String,                   // Name prefix of elastic index
  typeName: String,                    // Name of the metric type in ES
  dateFormat: String,                  // Date format for index suffixes
  templateName: String,                // Name of ES index template
  templateLocation: Option[String],    // Path to index template to use
  http: dispatch.Http,                 // HTTP driver
  groups: List[String],                // Subscription groups to publish to ES
  subscriptionTimeout: FiniteDuration, // Maximum interval for publishing to ES
  bufferSize: Int                      // Size of circular buffer in front of ES
)

import dispatch._, Defaults._

object ElasticCfg {
  def apply(
    url: String,
    indexName: String,
    typeName: String,
    dateFormat: String,
    templateName: String,
    templateLocation: Option[String],
    groups: List[String],
    subscriptionTimeout: FiniteDuration = 10.minutes,
    connectionTimeoutMs: Duration = 5000.milliseconds,
    bufferSize: Int = 4096
  ): ElasticCfg = {
    val driver: Http = Http.configure(
      _.setAllowPoolingConnection(true)
       .setConnectionTimeoutInMs(connectionTimeoutMs.toMillis.toInt))
    ElasticCfg(url, indexName, typeName, dateFormat,
               templateName, templateLocation, driver, groups, subscriptionTimeout, bufferSize)
  }
}
