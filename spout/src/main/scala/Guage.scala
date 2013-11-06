package intelmedia.ws.monitoring

import java.util.concurrent.atomic._
import java.util.concurrent.{ExecutorService, ScheduledExecutorService}
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import scalaz.concurrent.Strategy

trait Guage[K,A] extends Instrument[K] { self =>

  def set(a: A): Unit

  /**
   * Delay publishing updates to this guage for the
   * given duration after a call to `set`. If multiple
   * values are `set` within the timing window, only the
   * most recent value is published.
   */
  def buffer(d: Duration)(
             implicit S: ScheduledExecutorService = Monitoring.schedulingPool,
             S2: ExecutorService = Monitoring.defaultPool): Guage[K,A] = {
    if (d < (100 microseconds))
      sys.error("buffer size be at least 100 microseconds, was: " + d)
    val cur = new AtomicReference[A]
    val scheduled = new AtomicBoolean(false)
    val nanos = d.toNanos
    val later = Strategy.Executor(S2)
    new Guage[K,A] {
      def set(a: A): Unit = {
        cur.set(a)
        if (scheduled.compareAndSet(false,true)) {
          val task = new Runnable { def run = {
            scheduled.set(false)
            val a2 = cur.get
            // we don't want to hold up the scheduling thread,
            // as that could cause delays for other metrics,
            // so callback is run on `S2`
            later { self.set(a2) }
          }}
          S.schedule(task, nanos, TimeUnit.NANOSECONDS)
        }
      }
      def keys = self.keys
    }
  }
}

object Guage {

  def scale[K](k: Double)(g: Guage[K,Double]): Guage[K,Double] =
    new Guage[K,Double] {
      def set(d: Double): Unit =
        g.set(d * k)
      def keys = g.keys
    }

}
