package oncue.svc.laboratory

import funnel.elastic.ElasticCfg
import scala.concurrent.duration._

case class RiemannCfg(
  host: String,
  port: Int,
  ttl: Duration
)

case class Options(
  elastic: Option[ElasticCfg] = None,
  riemann: Option[RiemannCfg] = None,
  funnelPort: Int = 5775
)
