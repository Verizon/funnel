package intelmedia.ws.funnel

import com.twitter.algebird.Group
import intelmedia.ws.funnel.{Buffers => B}
import java.net.URL
import java.util.concurrent.{ExecutorService,TimeUnit}
import scala.concurrent.duration._
import scalaz.concurrent.Task
import scalaz.stream.Process

/**
 * Provider of counters, gauges, and timers, tied to some
 * `Monitoring` instance. Instruments returned by this class
 * may update multiple metrics, see `counter`, `gauge` and
 * `timer` methods for more information.
 */
class Instruments(window: Duration, monitoring: Monitoring = Monitoring.default) {

  /**
   * Return a `Counter` with the given starting count.
   * Keys updated by this `Counter` are `now/label`,
   * `previous/label` and `sliding/label`.
   * See [[intelmedia.ws.funnel.Periodic]].
   */
  def counter(label: String, init: Int = 0): Counter[Periodic[Double]] = {
    val c = new Counter[Periodic[Double]] {
      val count = B.resetEvery(window)(B.counter(init))
      val previousCount = B.emitEvery(window)(count)
      val slidingCount = B.sliding(window)(identity[Double])(Group.doubleGroup)
      val u: Units[Double] = Units.Count
      val (nowK, incrNow) = monitoring.topic(s"now/$label", u)(count)
      val (prevK, incrPrev) = monitoring.topic(s"previous/$label", u)(previousCount)
      val (slidingK, incrSliding) = monitoring.topic(s"sliding/$label", u)(slidingCount)
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
   * Records the elapsed time in the current period whenever the
   * returned `Gauge` is set. See `Elapsed.scala`.
   */
  private[funnel] def currentElapsed(label: String): Gauge[Continuous[Double], Unit] = {
    val (k, snk) = monitoring.topic[Unit,Double](label, Units.Seconds)(
      B.currentElapsed(window).map(_.toSeconds.toDouble))
    val g = new Gauge[Continuous[Double], Unit] {
      def set(u: Unit) = snk(u)
      def keys = Continuous(k)
    }
    g.set(())
    g

  }
  /**
   * Records the elapsed time in the current period whenever the
   * returned `Gauge` is set. See `Elapsed.scala`.
   */
  private[funnel] def currentRemaining(label: String): Gauge[Continuous[Double], Unit] = {
    val (k, snk) = monitoring.topic[Unit,Double](label, Units.Seconds)(
      B.currentRemaining(window).map(_.toSeconds.toDouble))
    val g = new Gauge[Continuous[Double], Unit] {
      def set(u: Unit) = snk(u)
      def keys = Continuous(k)
    }
    g.set(())
    g
  }

  /**
   * Records the elapsed time that the `Monitoring` instance has
   * been running whenver the returned `Gauge` is set. See `Elapsed.scala`.
   */
  private[funnel] def uptime(label: String): Gauge[Continuous[Double], Unit] = {
    val (k, snk) = monitoring.topic[Unit,Double](label, Units.Minutes)(
      B.elapsed.map(_.toSeconds.toDouble / 60))
    val g = new Gauge[Continuous[Double], Unit] {
      def set(u: Unit) = snk(u)
      def keys = Continuous(k)
    }
    g.set(())
    g
  }

  /**
   * Return a `Gauge` with the given starting value.
   * This gauge only updates the key `now/\$label`.
   * For a historical gauge that summarizes an entire
   * window of values as well, see `numericGauge`.
   */
  def gauge[A:Reportable](label: String, init: A,
                          units: Units[A] = Units.None): Gauge[Continuous[A],A] = {
    val g = new Gauge[Continuous[A],A] {
      val (key, snk) = monitoring.topic(s"now/$label", units)(B.resetEvery(window)(B.variable(init)))
      def set(a: A) = snk(_ => a)
      def keys = Continuous(key)

      set(init)
    }
    g.buffer(50 milliseconds)
  }

  def trafficLight(label: String): TrafficLight =
    TrafficLight(gauge(label, TrafficLight.Red, Units.TrafficLight))

  /**
   * Return a `Gauge` with the given starting value.
   * Unlike `gauge`, keys updated by this `Counter` are
   * `now/label`, `previous/label` and `sliding/label`.
   * See [[intelmedia.ws.funnel.Periodic]].
   */
  def numericGauge(label: String, init: Double,
                   units: Units[Stats] = Units.None): Gauge[Periodic[Stats],Double] = {
    val g = new Gauge[Periodic[Stats],Double] {
      val now = B.resetEvery(window)(B.stats)
      val prev = B.emitEvery(window)(now)
      val sliding = B.sliding(window)((d: Double) => Stats(d))(Stats.statsGroup)
      val (nowK, nowSnk) = monitoring.topic(s"now/$label", units)(now)
      val (prevK, prevSnk) = monitoring.topic(s"previous/$label", units)(prev)
      val (slidingK, slidingSnk) = monitoring.topic(s"sliding/$label", units)(sliding)
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
   * See [[intelmedia.ws.funnel.Periodic]].
   */
  def timer(label: String): Timer[Periodic[Stats]] = {
    val t = new Timer[Periodic[Stats]] {
      val timer = B.resetEvery(window)(B.stats)
      val previousTimer = B.emitEvery(window)(timer)
      val slidingTimer = B.sliding(window)((d: Double) => Stats(d))(Stats.statsGroup)
      val u: Units[Stats] = Units.Duration(TimeUnit.MILLISECONDS)
      val (nowK, nowSnk) = monitoring.topic(s"now/$label", u)(timer)
      val (prevK, prevSnk) = monitoring.topic(s"previous/$label", u)(previousTimer)
      val (slidingK, slidingSnk) = monitoring.topic(s"sliding/$label", u)(slidingTimer)
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
