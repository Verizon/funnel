package intelmedia.ws.commons.monitoring

import intelmedia.ws.commons.monitoring.{Buffers => B}
import scala.concurrent.duration._

/**
 * Wraps a `Monitoring` instance with friendlier API.
 */
class Instruments(window: Duration, monitoring: Monitoring) {

  def counter(label: String, init: Int = 0): Counter = new Counter {
    val snk = monitoring.topic_(label)(B.resetEvery(window)(B.counter(init)))
    def incrementBy(n: Int): Unit = snk(n)
  }

  def guage(label: String, init: String): Guage[String] = Guage[String] {
    monitoring.topic_(label)(B.resetEvery(window)(B.variable(init)))
  }

  def booleanGuage(label: String, init: Boolean = false): Guage[Boolean] =
    Guage[Boolean] {
      monitoring.topic_(label)(B.resetEvery(window)(B.variable(init)))
    }

  def timer(label: String): Timer = new Timer {
    val snk = monitoring.topic_(label)(B.resetEvery(window)(B.stats))
    def start: () => Unit = {
      val t0 = System.nanoTime
      () => { val elapsed = System.nanoTime - t0; snk(elapsed.toDouble) }
    }
  }
}

object Instruments {
  val fiveMinute: Instruments = instance(5 minutes)
  val oneMinute: Instruments = instance(1 minutes)
  val default = fiveMinute

  def instance(d: Duration, m: Monitoring = Monitoring.default): Instruments =
    new Instruments(d, m)

  val reqs = fiveMinute.counter("n:requests")
  val timer = fiveMinute.timer("t:query")

  reqs.increment
  reqs.incrementBy(10)
  reqs.decrement
  timer.time {
    1 + 1 // do some stuff
  }

}
