package funnel
package flask

import funnel.elastic.ElasticCfg
import scala.concurrent.duration._

case class RiemannCfg(
  host: String,
  port: Int,
  ttl: Duration
)

case class Options(
  name: Option[String],
  cluster: Option[String],
  retriesDuration: Duration,
  maxRetries: Int,
  elastic: Option[ElasticCfg] = None,
  riemann: Option[RiemannCfg] = None,
  collectLocalMetrics: Option[Boolean] = None,
  localMetricFrequency: Option[Int] = None,
  funnelPort: Int = 5775,
  selfiePort: Int = 7557,
  metricTTL: Option[Duration] = None,
  telemetryPort: Int = 7390
)
