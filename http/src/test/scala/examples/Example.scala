package intelmedia.ws.funnel
package examples

import org.scalacheck._
import Prop._
import scala.concurrent.duration._

object Example extends Properties("example") {

  object metrics {
    import intelmedia.ws.monitoring.instruments._
    val reqs = counter("requests#")
    val dbOk = gauge("db-up?", true)
    val query = timer("query-speed")

    // verify that we haven't received more than 20k req,
    // the db is up, and our average query response time
    // is under 20 millis

    val healthy: Metric[Boolean] = for {
      n <- reqs.key
      db <- dbOk.key
      t <- query.key
    } yield n < 20000 &&
            db &&
            t.mean < 20

    healthy.publishEvery(5 seconds)("status")
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
