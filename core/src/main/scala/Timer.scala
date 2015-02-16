package funnel

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

  /**
   * Evaluate `a` and record its evaluation time even if
   * evaluation completes with an error. Use `timeSuccess`
   * if you'd like to record a time only in the sucsessful case.
   */
  def time[A](a: => A): A = {
    val stop = this.start
    try a
    finally stop()
  }

  /**
   * Like `time`, but records a time only if evaluation of
   * `a` completes without error.
   */
  def timeSuccess[A](a: => A): A = {
    val stop = this.start
    val result = a
    stop()
    result
  }

  /**
   * Time a `Future` by registering a callback on its
   * `onComplete` method. The stopwatch begins now.
   * This function records a time regardless if the `Future`
   * completes with an error or not. Use `timeFutureSuccess` or
   * explicit calls to `start` and `stop` if you'd like to
   * record a time only in the event the `Future` succeeds.
   */
  def timeFuture[A](f: Future[A])(implicit ctx: ExecutionContext = ExecutionContext.Implicits.global): Future[A] = {
    timeAsync(f.onComplete)
    f
  }

  /**
   * Like `timeFuture`, but records a time only if `f` completes
   * without an exception.
   */
  def timeFutureSuccess[A](f: Future[A])(implicit ctx: ExecutionContext = ExecutionContext.Implicits.global): Future[A] = {
    timeAsync((cb: A => Unit) => f.onSuccess({ case a => cb(a) }))
    f
  }

  /**
   * Time an asynchronous `Task`. The stopwatch begins running when
   * the returned `Task` is run and a stop time is recorded if the
   * `Task` completes in any state. Use `timeTaskSuccess` if you
   * wish to only record times when the `Task` succeeds.
   */
  def timeTask[A](a: Task[A]): Task[A] =
    Task.delay(start).flatMap { stopwatch =>
      a.attempt.flatMap { a =>
        stop(stopwatch)
        a.fold(Task.fail, Task.now)
      }
    }

  /**
   * Like `timeTask`, but records a time even if the `Task` completes
   * with an error.
   */
  def timeTaskSuccess[A](a: Task[A]): Task[A] =
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
    val nonce = new AtomicLong(0)
    val n = new AtomicInteger(0)
    val totalNanos = new AtomicLong(0)
    val scheduled = new AtomicBoolean(false)
    val nanos = d.toNanos
    val later = Strategy.Executor(S2)
    new Timer[K] {
      def recordNanos(delta: Long): Unit = {
        val _1 = nonce.incrementAndGet
        val _2 = totalNanos.addAndGet(delta)
        val _3 = nonce.incrementAndGet
        val _4 = n.incrementAndGet

        if (scheduled.compareAndSet(false, true)) {
          val task = new Runnable { def run = {
            scheduled.set(false)
            @annotation.tailrec
            def go: Unit = {
              val id = nonce.get
              val snapshotN = n.get
              val snapshotT = totalNanos.get
              if (nonce.get == id) { // we got a consistent snapshot
                val _1 = n.addAndGet(-snapshotN)
                val _2 = totalNanos.addAndGet(-snapshotT)
                val d = (snapshotT / snapshotN.toDouble).toLong
                // we don't want to hold up the scheduling thread,
                // as that could cause delays for other metrics,
                // so callback is run on `S2`
                val _ = later { self.recordNanos(d) }
                ()
              }
              else go
            }
            go
          }}
          val _ = S.schedule(task, nanos, TimeUnit.NANOSECONDS)
          ()
        }
      }
      def keys = self.keys
    }
  }
}
