package funnel

import com.twitter.algebird.Group
import funnel.{Buffers => B}
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
class Instruments(val window: Duration,
                  val monitoring: Monitoring = Monitoring.default,
                  val bufferTime: Duration = 200 milliseconds) {

  private def nowL(s: String) = if (s == "") s else s"$s (now)"
  private def previousL(s: String) = if (s == "") s else s"$s ($window ago)"
  private def slidingL(s: String) = if (s == "") s else s"$s (past $window)"

  /**
    * takes a Key => Key and also sets the kind attribute
    */
  private def andKind[A](kind: String, keyMod: Key[A] => Key[A]): Key[A] => Key[A] =
    keyMod compose (k => k.copy(attributes = k.attributes + ("kind" -> kind)))

  /**
   * Return a `Counter` with the given starting count.
   * Keys updated by this `Counter` are `now/label`,
   * `previous/label` and `sliding/label`.
   * See [[funnel.Periodic]].
   */
  def counter(label: String,
              init: Int = 0,
              description: String = "",
              keyMod: Key[Double] => Key[Double] = identity): Counter[Periodic[Double]] = {
    val kinded = andKind("counter", keyMod)
    val c = new Counter[Periodic[Double]] {
      val count = B.resetEvery(window)(B.counter(init))
      val previousCount = B.emitEvery(window)(count)
      val slidingCount = B.sliding(window)(identity[Double])(Group.doubleGroup)
      val u: Units/*[Double]*/ = Units.Count
      val (nowK, incrNow) =
        monitoring.topic[Long,Double](s"now/$label", u, nowL(description), kinded)(count)
      val (prevK, incrPrev) =
        monitoring.topic[Long,Double](s"previous/$label", u, previousL(description), kinded)(previousCount)
      val (slidingK, incrSliding) =
        monitoring.topic[Double,Double](s"sliding/$label", u, slidingL(description), kinded)(slidingCount)
      def incrementBy(n: Int): Unit = {
        incrNow(n); incrPrev(n); incrSliding(n)
      }
      def keys = Periodic(nowK, prevK, slidingK)

      incrementBy(0)
    }
    c.buffer(bufferTime) // only publish updates this often
  }

  // todo: histogramgauge, histogramCount, histogramTimer
  // or maybe we just modify the existing combinators to
  // update some additional values

  /**
   * Records the elapsed time in the current period whenever the
   * returned `Gauge` is set. See `Elapsed.scala`.
   */
  private[funnel] def currentElapsed(label: String,
                                     desc: String,
                                     keyMod: Key[Double] => Key[Double] = identity): Gauge[Continuous[Double], Unit] = {
    val kinded = andKind("timer", keyMod)
    val (k, snk) = monitoring.topic[Unit,Double](label, Units.Seconds, desc, kinded)(
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
  private[funnel] def currentRemaining(label: String,
                                       desc: String): Gauge[Continuous[Double], Unit] = {
    val kinded = andKind("timer", identity[Key[Double]])
    val (k, snk) = monitoring.topic[Unit,Double](label, Units.Seconds, desc, kinded)(
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
    val kinded = andKind("timer", identity[Key[Double]])
    val (k, snk) = monitoring.topic[Unit,Double](label,
                                                 Units.Minutes,
                                                 "Time elapsed since monitoring started",
                                                 kinded)(
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
                          units: Units/*[A]*/ = Units.None,
                          description: String = "",
                          keyMod: Key[A] => Key[A] = {(k:Key[A]) => k}): Gauge[Continuous[A],A] = {
    val kinded = andKind("gauge",keyMod)
    val g = new Gauge[Continuous[A],A] {
      val (key, snk) = monitoring.topic(s"now/$label", units, description, kinded)(B.resetEvery(window)(B.variable(init)))
      def set(a: A) = snk(_ => a)
      def keys = Continuous(key)

      set(init)
    }
    g.buffer(bufferTime)
  }

  def trafficLight(label: String,
                   description: String = "",
                   keyMod: Key[String] => Key[String] = identity[Key[String]]): TrafficLight =
    TrafficLight(gauge(label, TrafficLight.Red, Units.TrafficLight, description, andKind("traffic", keyMod)))

  /**
   * Return a `Gauge` with the given starting value.
   * Unlike `gauge`, keys updated by this `Counter` are
   * `now/label`, `previous/label` and `sliding/label`.
   * See [[funnel.Periodic]].
   */
  def numericGauge(label: String, init: Double,
                   units: Units/*[Stats]*/ = Units.None,
                   description: String = "",
                   keyMod: Key[Stats] => Key[Stats] = identity): Gauge[Periodic[Stats],Double] = {
    val kinded = andKind("numeric", keyMod)
    val g = new Gauge[Periodic[Stats],Double] {
      val now = B.resetEvery(window)(B.stats)
      val prev = B.emitEvery(window)(now)
      val sliding = B.sliding(window)((d: Double) => Stats(d))(Stats.statsGroup)
      val (nowK, nowSnk) =
        monitoring.topic[Double,Stats](s"now/$label", units, nowL(description), kinded)(now)
      val (prevK, prevSnk) =
        monitoring.topic[Double,Stats](s"previous/$label", units, previousL(description), kinded)(prev)
      val (slidingK, slidingSnk) =
        monitoring.topic[Double,Stats](s"sliding/$label", units, slidingL(description), kinded)(sliding)
      def keys = Periodic(nowK, prevK, slidingK)
      def set(d: Double): Unit = {
        nowSnk(d); prevSnk(d); slidingSnk(d)
      }
      set(init)
    }
    g.buffer(bufferTime)
  }

  /**
   * Return a `Timer` which updates the following keys:
   * `now/label`, `previous/label`, and `sliding/label`.
   * See [[funnel.Periodic]].
   */
  def timer(label: String,
            description: String = "",
            keyMod: Key[Stats] => Key[Stats] = identity): Timer[Periodic[Stats]] = {
    val kinded = andKind("timer", keyMod)
    val t = new Timer[Periodic[Stats]] {
      val timer = B.resetEvery(window)(B.stats)
      val previousTimer = B.emitEvery(window)(timer)
      val slidingTimer = B.sliding(window)((d: Double) => Stats(d))(Stats.statsGroup)
      val u: Units/*[Stats]*/ = Units.Duration(TimeUnit.MILLISECONDS)
      val (nowK, nowSnk) =
        monitoring.topic[Double, Stats](s"now/$label", u, nowL(description), kinded)(timer)
      val (prevK, prevSnk) =
        monitoring.topic[Double, Stats](s"previous/$label", u, previousL(description), kinded)(previousTimer)
      val (slidingK, slidingSnk) =
        monitoring.topic[Double, Stats](s"sliding/$label", u, slidingL(description), kinded)(slidingTimer)
      def keys = Periodic(nowK, prevK, slidingK)
      def recordNanos(nanos: Long): Unit = {
        // record time in milliseconds
        val millis = nanos.toDouble / 1e6
        nowSnk(millis); prevSnk(millis); slidingSnk(millis)
      }
    }
    t.buffer(bufferTime)
  }

}
