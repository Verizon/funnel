package intelmedia.ws.monitoring

import com.twitter.algebird.Group
import intelmedia.ws.monitoring.{Buffers => B}
import scala.concurrent.duration._

/**
 * Provider of counters, gauges, and timers, tied to some
 * `Monitoring` server instance.
 */
class Instruments(window: Duration, monitoring: Monitoring = Monitoring.default) {

  /**
   * Return a `Counter` with the given starting count.
   * Keys updated by this `Counter` are `now/label`,
   * `previous/label` and `sliding/label`.
   * See [[intelmedia.ws.monitoring.Periodic]].
   */
  def counter(label: String, init: Int = 0): Counter[Periodic[Int]] = {
    val c = new Counter[Periodic[Int]] {
      val count = B.resetEvery(window)(B.counter(init))
      val previousCount = B.emitEvery(window)(count)
      val slidingCount = B.sliding(window)(identity[Int])(Group.intGroup)
      val (nowK, incrNow) = monitoring.topic(s"now/$label")(count)
      val (prevK, incrPrev) = monitoring.topic(s"previous/$label")(previousCount)
      val (slidingK, incrSliding) = monitoring.topic(s"sliding/$label")(slidingCount)
      def incrementBy(n: Int): Unit = {
        incrNow(n); incrPrev(n); incrSliding(n)
      }
      def keys = Periodic(nowK, prevK, slidingK)

      incrementBy(0)
    }
    c.buffer(50 milliseconds) // only publish updates this often
  }

  // todo: histogramgauge, histogramCount, histogramTimer
  // or maybe we just modify the existing combinators to
  // update some additional values

  /**
   * Return a `Gauge` with the given starting value.
   * This gauge only updates the key `now/$label`.
   * For a historical gauge that summarizes an entire
   * window of values as well, see `numericGauge`.
   */
  def gauge[A <% Reportable[A]](label: String, init: A): Gauge[Continuous[A],A] = {
    val g = new Gauge[Continuous[A],A] {
      val (key, snk) = monitoring.topic(s"now/$label")(B.resetEvery(window)(B.variable(init)))
      def set(a: A) = snk(_ => a)
      def keys = Continuous(key)

      set(init)
    }
    g.buffer(50 milliseconds)
  }

  /**
   * Return a `Gauge` with the given starting value.
   * Unlike `gauge`, keys updated by this `Counter` are
   * `now/label`, `previous/label` and `sliding/label`.
   * See [[intelmedia.ws.monitoring.Periodic]].
   */
  def numericGauge(label: String, init: Double): Gauge[Periodic[Stats],Double] = {
    val g = new Gauge[Periodic[Stats],Double] {
      val now = B.resetEvery(window)(B.stats)
      val prev = B.emitEvery(window)(now)
      val sliding = B.sliding(window)((d: Double) => Stats(d))(Stats.statsGroup)
      val (nowK, nowSnk) = monitoring.topic(s"now/$label")(now)
      val (prevK, prevSnk) = monitoring.topic(s"previous/$label")(prev)
      val (slidingK, slidingSnk) = monitoring.topic(s"sliding/$label")(sliding)
      def keys = Periodic(nowK, prevK, slidingK)
      def set(d: Double): Unit = {
        nowSnk(d); prevSnk(d); slidingSnk(d)
      }
      set(init)
    }
    g.buffer(50 milliseconds)
  }

  /**
   * Return a `Timer` which updates the following keys:
   * `now/label`, `previous/label`, and `sliding/label`.
   * See [[intelmedia.ws.monitoring.Periodic]].
   */
  def timer(label: String): Timer[Periodic[Stats]] = {
    val t = new Timer[Periodic[Stats]] {
      val timer = B.resetEvery(window)(B.stats)
      val previousTimer = B.emitEvery(window)(timer)
      val slidingTimer = B.sliding(window)((d: Double) => Stats(d))(Stats.statsGroup)
      val (nowK, nowSnk) = monitoring.topic(s"now/$label")(timer)
      val (prevK, prevSnk) = monitoring.topic(s"previous/$label")(previousTimer)
      val (slidingK, slidingSnk) = monitoring.topic(s"sliding/$label")(slidingTimer)
      def keys = Periodic(nowK, prevK, slidingK)
      def recordNanos(nanos: Long): Unit = {
        // record time in milliseconds
        val millis = nanos.toDouble / 1e6
        nowSnk(millis); prevSnk(millis); slidingSnk(millis)
      }
    }
    t.buffer(50 milliseconds)
  }
}
