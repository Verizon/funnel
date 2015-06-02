package funnel

import com.twitter.algebird.Group
import funnel.{Buffers => B}
import java.net.URL
import java.util.concurrent.{ExecutorService,TimeUnit}
import scala.concurrent.duration._
import scalaz.concurrent.Task
import scalaz.stream.Process
import scalaz.std.tuple._
import scalaz.std.list._
import scalaz.syntax.functor._
import scalaz.syntax.foldable._
import scalaz.stream.process1.lift

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

  import monitoring.runLogging

  /**
    * takes a Key => Key and also sets the kind attribute
    */
  private def andKind[A](kind: String, keyMod: Key[A] => Key[A]): Key[A] => Key[A] =
    keyMod compose (k => k.copy(attributes = k.attributes + ("kind" -> kind)))

  /**
   * Return a `PeriodicGauge` with the given starting value.
   * Keys updated by this `PeriodicGauge` are `now/label`,
   * `previous/label` and `sliding/label`.
   * See [[funnel.Periodic]].
   */
  def periodicGauge[O:Reportable:Group](
    label: String, units: Units = Units.None, description: String = "", init: O,
    keyMod: Key[O] => Key[O] = identity[Key[O]] _): PeriodicGauge[O] = {
      val O = implicitly[Group[O]]
      val kinded = andKind("periodic", keyMod)
      val c = new PeriodicGauge[O] {
        val now = B.resetEvery(window)(B.accum[O,O](init)(O.plus))
        val prev = B.emitEvery(window)(now)
        val sliding = B.sliding(window)(identity[O])(O)
        val (nowK, incrNow) =
          monitoring.topic[O,O](
            s"now/$label", units, nowL(description), kinded)(now)
        val (prevK, incrPrev) =
          monitoring.topic[O,O](
            s"previous/$label", units, previousL(description), kinded)(prev)
        val (slidingK, incrSliding) =
          monitoring.topic[O,O](
            s"sliding/$label", units, slidingL(description), kinded)(sliding)
        def append(n: O): Unit = {
          incrNow(n); incrPrev(n); incrSliding(n)
        }
        def keys = Periodic[O](nowK, prevK, slidingK)
      }
      c.buffer(bufferTime) // only publish updates this often
  }

  /**
   * Return a `Counter` with the given starting count.
   * Keys updated by this `Counter` are `now/label`,
   * `previous/label` and `sliding/label`.
   * See [[funnel.Periodic]].
   * You should use counters only for metrics that are monotonic.
   */
  def counter(label: String,
              init: Int = 0,
              description: String = "",
              keyMod: Key[Double] => Key[Double] = identity): Counter =
    new Counter(periodicGauge[Double](
      label, Units.Count, description, init, andKind("counter", keyMod)))

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
      B.currentElapsed(window).map(_.toSeconds.toDouble)).map(_.run)
    val g = new Gauge[Continuous[Double], Unit] {
      def set(u: Unit) = runLogging(snk(u))
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
      B.currentRemaining(window).map(_.toSeconds.toDouble)).map(_.run)
    val g = new Gauge[Continuous[Double], Unit] {
      def set(u: Unit) = runLogging(snk(u))
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
      B.elapsed.map(_.toSeconds.toDouble / 60)).map(_.run)
    val g = new Gauge[Continuous[Double], Unit] {
      def set(u: Unit) = runLogging(snk(u))
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
                          units: Units = Units.None,
                          description: String = "",
                          keyMod: Key[A] => Key[A] = identity[Key[A]] _): Gauge[Continuous[A],A] = {
    val kinded = andKind("gauge",keyMod)
    val g = new Gauge[Continuous[A],A] {
      val (key, snk) = monitoring.topic(s"now/$label", units, description, kinded)(B.resetEvery(window)(B.variable(init))).map(_.run)
      def set(a: A) = runLogging(snk(_ => a))
      def keys = Continuous(key)

      set(init)
    }
    g.buffer(bufferTime)
  }

  /**
   * Return an `Edge` with the given initial `origin` and `destination`.
   * See [[Edge]] for more information.
   *
   * This will create the following instruments and keys:
   *
   * A string gauge  `now/$$label/origin`.
   * A string gauge  `now/$$label/destination`.
   * A timer         `?/$$label/timer` where `?` is `now`, `previous`, and `sliding`.
   * A traffic light `now/$$label/status`
   *
   * @param label The name of the traffic light metric
   * @param description A human-readable descirption of the semantics of this metric
   * @param origin The source of the request. Typically this should be the IP address of the calling host
   * @param destination The target of the outbound request; typically this is IP or DNS.
   */
  def edge(
    label: String,
    description: String = "",
    origin: Edge.Origin,
    destination: Edge.Destination): Edge = {
      def addEdge[A](k: Key[A]): Key[A] = k.setAttribute("edge", label)
      Edge(
        origin = gauge(
          label  = s"$label/origin",
          init   = origin,
          keyMod = addEdge[Edge.Origin]),
        destination = gauge(
          label  = s"$label/destination",
          init   = destination,
          keyMod = addEdge[Edge.Destination]),
        timer = timer(
          label  = s"$label/timer",
          keyMod = addEdge),
        status = trafficLight(
          label  = s"$label/status",
          keyMod = addEdge)
      )
  }

  /**
   * Return a `TrafficLight`--a gauge whose value can be `Red`, `Amber`,
   * or `Green`. The initial value is `Red`. The key is `now/$$label`.
   *
   * @param label The name of the traffic light metric
   * @param description A human-readable descirption of the semantics of this metric
   */
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
                   units: Units = Units.None,
                   description: String = "",
                   keyMod: Key[Stats] => Key[Stats] = identity): Gauge[Periodic[Stats],Double] = {
    val kinded = andKind("numeric", keyMod)
    val g = new Gauge[Periodic[Stats],Double] {
      val now = B.resetEvery(window)(B.stats)
      val prev = B.emitEvery(window)(now)
      val sliding = B.sliding(window)((d: Double) => Stats(d))(Stats.statsGroup)
      val (nowK, nowSnk) =
        monitoring.topic[Double,Stats](s"now/$label", units, nowL(description), kinded)(now).map(_.run)
      val (prevK, prevSnk) =
        monitoring.topic[Double,Stats](s"previous/$label", units, previousL(description), kinded)(prev).map(_.run)
      val (slidingK, slidingSnk) =
        monitoring.topic[Double,Stats](s"sliding/$label", units, slidingL(description), kinded)(sliding).map(_.run)
      def keys = Periodic(nowK, prevK, slidingK)
      def set(d: Double): Unit = runLogging(List(
        nowSnk, prevSnk, slidingSnk).traverse_(_(d)))
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
      val u: Units = Units.Duration(TimeUnit.MILLISECONDS)
      val (nowK, nowSnk) =
        monitoring.topic[Double, Stats](s"now/$label", u, nowL(description), kinded)(timer).map(_.run)
      val (prevK, prevSnk) =
        monitoring.topic[Double, Stats](s"previous/$label", u, previousL(description), kinded)(previousTimer).map(_.run)
      val (slidingK, slidingSnk) =
        monitoring.topic[Double, Stats](s"sliding/$label", u, slidingL(description), kinded)(slidingTimer).map(_.run)
      def keys = Periodic(nowK, prevK, slidingK)
      def recordNanos(nanos: Long): Unit = {
        // record time in milliseconds
        val millis = nanos.toDouble / 1e6
        runLogging(List(nowSnk, prevSnk, slidingSnk).traverse_(_(millis)))
      }
    }
    t.buffer(bufferTime)
  }

}
