package intelmedia.ws.monitoring

import com.twitter.algebird.Group
import intelmedia.ws.monitoring.{Buffers => B}
import scala.concurrent.duration._

/**
 * Provider of counters, guages, and timers, tied to some
 * `Monitoring` server instance.
 */
class Instruments(window: Duration, monitoring: Monitoring) {

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
    c.buffer(100 milliseconds) // only publish updates this often
  }

  // todo: histogramGuage, histogramCount, histogramTimer
  // or maybe we just modify the existing combinators to
  // update some additional values

  /**
   * Return a `Guage` with the given starting value.
   * This guage only updates the key `now/$label`.
   * For a historical guage that summarizes an entire
   * window of values as well, see `numericGuage`.
   */
  def guage[A <% Reportable[A]](label: String, init: A): Guage[Continuous[A],A] = new Guage[Continuous[A],A] {
    val (key, snk) = monitoring.topic(s"now/$label")(B.resetEvery(window)(B.variable(init)))
    def set(a: A) = snk(_ => a)
    def keys = Continuous(key)

    set(init)
  }

  /**
   * Return a `Guage` with the given starting value.
   * Unlike `guage`, keys updated by this `Counter` are
   * `now/label`, `previous/label` and `sliding/label`.
   * See [[intelmedia.ws.monitoring.Periodic]].
   */
  def numericGuage(label: String, init: Double): Guage[Periodic[Stats],Double] = new Guage[Periodic[Stats],Double] {
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

  /**
   * Return a `Timer` which updates the following keys:
   * `now/label`, `previous/label`, and `sliding/label`.
   * See [[intelmedia.ws.monitoring.Periodic]].
   */
  def timer(label: String): Timer[Periodic[Stats]] = new Timer[Periodic[Stats]] {
    val timer = B.resetEvery(window)(B.stats)
    val previousTimer = B.emitEvery(window)(timer)
    val slidingTimer = B.sliding(window)((d: Double) => Stats(d))(Stats.statsGroup)
    val (nowK, nowSnk) = monitoring.topic(s"now/$label")(timer)
    val (prevK, prevSnk) = monitoring.topic(s"previous/$label")(previousTimer)
    val (slidingK, slidingSnk) = monitoring.topic(s"sliding/$label")(slidingTimer)
    def keys = Periodic(nowK, prevK, slidingK)
    def start: () => Unit = {
      val t0 = System.nanoTime
      () => {
        // record time in milliseconds
        val elapsed = (System.nanoTime - t0).toDouble / 1e6
        nowSnk(elapsed); prevSnk(elapsed); slidingSnk(elapsed)
      }
    }
  }
}

object Instruments {
  val fiveMinute: Instruments = instance(5 minutes)
  val oneMinute: Instruments = instance(1 minutes)
  val default = {
    val r = fiveMinute
    JVM.instrument(fiveMinute)
    r
  }

  def instance(d: Duration, m: Monitoring = Monitoring.default): Instruments =
    new Instruments(d, m)
}
