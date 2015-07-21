package funnel

import java.util.concurrent.{ExecutorService, ScheduledExecutorService}
import scala.concurrent.duration._
import scalaz.concurrent.{Strategy, Task}
import scalaz.stream.Process
import scalaz.stream.time.awakeEvery

object Events {

  /**
   * A discrete, infinite stream of 'ticks', typically used
   * to control scheduling of some other action.
   */
  type Event = Monitoring => Process[Task, Unit]

  /** An event which fires at the supplied regular interval. */
  def every(d: Duration)(
    implicit schedulingPool: ScheduledExecutorService = Monitoring.schedulingPool):
      Event = _ => awakeEvery(d)(Strategy.Naive, schedulingPool).map(_ => ())

  /**
   * The first `n` ticks of an event which fires at the supplied
   * regular interval.
   */
  def takeEvery(d: Duration, n: Int)(
    implicit pool: ExecutorService = Monitoring.defaultPool,
    schedulingPool: ScheduledExecutorService = Monitoring.schedulingPool):
    Event = every(d) andThen (_.take(n))

  /** An event which fires whenever the given `Key` is updated. */
  def changed[A](k: Key[A]): Event = m => m.get(k).changes

  /** An event which fires whenever either event fires. */
  def or(e1: Event, e2: Event): Event =
    m => e1(m) merge e2(m)

  /** An event which waits for both events to fire. */
  def and(e1: Event, e2: Event): Event =
    m => {
      val s1 = e1(m)
      val s2 = e2(m)
      s1.flatMap(_ => s2.once)
    }

  // def exponentialBackoff(failure: Signal[Boolean], base: Duration): Event
  // def exponentialBackoff(failure: Key[Boolean], base: Duration): Event
}

