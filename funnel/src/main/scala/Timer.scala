package intelmedia.ws.monitoring

import java.util.concurrent.atomic._
import java.util.concurrent.TimeUnit
import java.util.concurrent.{ConcurrentLinkedQueue, ExecutorService, ScheduledExecutorService}
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext,Future}
import scalaz.concurrent.Strategy
import scalaz.concurrent.Task

trait Timer[K] extends Instrument[K] { self =>

  /** Record the given duration, in nanoseconds. */
  def recordNanos(nanos: Long): Unit

  /** Record the given duration. */
  def record(d: Duration): Unit = recordNanos(d.toNanos)

  /**
   * Return a newly running stopwatch. To record
   * a time, call the returned stopwatch. Example:
   *
   *    val T: Timer = ...
   *    val stopwatch = T.start
   *    doSomeStuff()
   *    // ... and we're done
   *    stopwatch()
   *    // alternately, `T.stop(stopwatch)`
   *
   * Reusing a stopwatch is not recommended; it will
   * record the time since the stopwatch was first
   * created.
   */
  def start: () => Unit = {
    val t = System.nanoTime
    () => recordNanos(System.nanoTime - t)
  }

  /** A bit of syntax for stopping a stopwatch returned from `start`. */
  def stop(stopwatch: () => Unit): Unit = stopwatch()

  /** Evaluate `a` and record its evaluation time. */
  def time[A](a: => A): A = {
    val stop = this.start
    try a
    finally stop()
  }

  /**
   * Time a `Future` by registering a callback on its
   * `onComplete` method. The stopwatch begins now.
   */
  def timeFuture[A](f: Future[A])(implicit ctx: ExecutionContext = ExecutionContext.Implicits.global): Future[A] = {
    timeAsync(f.onComplete)
    f
  }

  /**
   * Time an asynchronous `Task`. The stopwatch begins running
   * when the returned `Task` is run.
   */
  def timeTask[A](a: Task[A]): Task[A] =
    Task.delay(start).flatMap { stopwatch =>
      a.map { a => stop(stopwatch); a }
    }

  /**
   * Time a currently running asynchronous task. The
   * stopwatch begins now, and finishes when the
   * callback is invoked with the result.
   */
  def timeAsync[A](register: (A => Unit) => Unit): Unit = {
    val stopwatch = this.start
    register((a => stop(stopwatch)))
  }

  /**
   * Delay publishing updates to this `Timer` for the
   * given duration after modification. If multiple
   * timings are recorded within the window, only the
   * average of these timings is published.
   */
  def buffer(d: Duration)(
             implicit S: ScheduledExecutorService = Monitoring.schedulingPool,
             S2: ExecutorService = Monitoring.defaultPool): Timer[K] = {
    if (d < (100 microseconds))
      sys.error("buffer size be at least 100 microseconds, was: " + d)
    val reading = new AtomicBoolean(false)
    val nonce = new AtomicLong(0)
    val n = new AtomicInteger(0)
    val totalNanos = new AtomicLong(0)
    val scheduled = new AtomicBoolean(false)
    val nanos = d.toNanos
    val later = Strategy.Executor(S2)
    val pending = new ConcurrentLinkedQueue[Long]
    new Timer[K] {
      def recordNanos(delta: Long): Unit = {
        nonce.incrementAndGet
        totalNanos.addAndGet(delta)
        nonce.incrementAndGet
        n.incrementAndGet

        if (scheduled.compareAndSet(false, true)) {
          val task = new Runnable { def run = {
            scheduled.set(false)
            @annotation.tailrec
            def go: Unit = {
              val id = nonce.get
              val snapshotN = n.get
              val snapshotT = totalNanos.get
              if (nonce.get == id) { // we got a consistent snapshot
                n.addAndGet(-snapshotN)
                totalNanos.addAndGet(-snapshotT)
                val d = (snapshotT / snapshotN.toDouble).toLong
                // we don't want to hold up the scheduling thread,
                // as that could cause delays for other metrics,
                // so callback is run on `S2`
                later { self.recordNanos(d) }
              }
              else go
            }
            go
          }}
          S.schedule(task, nanos, TimeUnit.NANOSECONDS)
        }
      }
      def keys = self.keys
    }
  }
}
