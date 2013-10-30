package intelmedia.ws.monitoring

import scala.concurrent.{ExecutionContext,Future}
import scalaz.concurrent.Task

trait Timer[K] extends Instrument[K] {

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
  def start: () => Unit

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
}
