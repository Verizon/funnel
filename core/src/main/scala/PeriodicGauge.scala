package funnel

import com.twitter.algebird.Group
import java.util.concurrent.{ExecutorService, ScheduledExecutorService}
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import scalaz.concurrent.Strategy

/**
 * A gauge whose readout value type is characterized by a `Group`.
 *
 * See http://en.wikipedia.org/wiki/Group_%28mathematics%29
 */
abstract class PeriodicGauge[A](implicit A: Group[A]) extends Instrument[Periodic[A]] { self =>
  def append(a: A): Unit
  final def remove(a: A): Unit =
    append(A.negate(a))

  /**
   * Delay publishing updates to this `GroupGauge` for the
   * given duration after modification.
   */
  def buffer(d: Duration)(
             implicit S: ScheduledExecutorService = Monitoring.schedulingPool,
             S2: ExecutorService = Monitoring.defaultPool): PeriodicGauge[A] =
    new PeriodicGauge[A] {
      val b = new Gauge.Buffer(d, A.zero)(A.plus, _ => A.zero, self.append)
      def append(a: A) = b(a)
      def keys = self.keys
    }
}

