package intelmedia.ws.monitoring
package examples

import org.scalacheck._
import Prop._

object Example extends Properties("example") {

  object metrics {
    val M = Instruments.default // resets every 5 minutes
    val reqs = M.counter("requests#")
    val dbOk = M.guage("db-up?", true)
    val query = M.timer("query-speed")
  }

  property("example") = secure {

    metrics.query.time {
      if (true) metrics.dbOk.set(true)
      metrics.reqs.increment
      metrics.reqs.incrementBy(10)
    }
    true
  }
}
