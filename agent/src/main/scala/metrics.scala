package funnel
package agent

import instruments._

object metrics {
  object http {
    val MetricLatency = timer("http/post/metrics")
  }
}
