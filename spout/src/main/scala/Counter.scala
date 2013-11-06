package intelmedia.ws.monitoring

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

  def buffer(d: Duration)(
             implicit S: ScheduledExecutorService = Monitoring.schedulingPool,
             S2: ExecutorService = Monitoring.defaultPool): Counter[K] = {
    val delta = new AtomicInteger(0)
    val scheduled = new AtomicBoolean(false)
    val nanos = d.toNanos
    val later = Strategy.Executor(S2)
    new Counter[K] {
      def incrementBy(by: Int): Unit = {
        delta.addAndGet(by)
        if (scheduled.compareAndSet(false,true)) {
          val task = new Runnable { def run = {
            try {
              val d = delta.get
              delta.addAndGet(-d)
              // we don't want to hold up the scheduling thread,
              // as that could cause delays for other metrics,
              // so callback is run on `S2`
              later { self.incrementBy(d) }
            }
            finally scheduled.set(false)
          }}
          S.schedule(task, nanos, TimeUnit.NANOSECONDS)
        }
      }
      def keys = self.keys
    }
  }
}
