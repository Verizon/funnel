package funnel

import java.util.concurrent.atomic._
import java.util.concurrent.{ExecutorService, ScheduledExecutorService}
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import scalaz.concurrent.Strategy

trait Counter[K] extends Instrument[K] { self =>
  def incrementBy(by: Int): Unit
  def increment: Unit = incrementBy(1)
  def decrement: Unit = incrementBy(-1)
  def decrementBy(by: Int): Unit = incrementBy(-by)

  /**
   * Delay publishing updates to this `Counter` for the
   * given duration after modification.
   */
  def buffer(d: Duration)(
             implicit S: ScheduledExecutorService = Monitoring.schedulingPool,
             S2: ExecutorService = Monitoring.defaultPool): Counter[K] = {
    if (d < (100 microseconds))
      sys.error("buffer size be at least 100 microseconds, was: " + d)
    val delta = new AtomicInteger(0)
    val scheduled = new AtomicBoolean(false)
    val nanos = d.toNanos
    val later = Strategy.Executor(S2)
    new Counter[K] {
      def incrementBy(by: Int): Unit = {
        val _ = delta.addAndGet(by)
        if (scheduled.compareAndSet(false,true)) {
          val task = new Runnable { def run = {
            scheduled.set(false)
            val d = delta.get
            val _ = delta.addAndGet(-d)
            // we don't want to hold up the scheduling thread,
            // as that could cause delays for other metrics,
            // so callback is run on `S2`
            val __ = later { self.incrementBy(d) }
            ()
          }}
          val _ = S.schedule(task, nanos, TimeUnit.NANOSECONDS)
          ()
        }
      }
      def keys = self.keys
    }
  }
}
