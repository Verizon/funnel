package funnel
package chemist

import funnel.internals._
import Sharding.Distribution
import scalaz.concurrent.Task

/**
 * Holds a stale, non-authoritve view on the state of the world.
 * The sole purpose of this class in life is to provide some cached
 * data that the HTTP API can use to answer user questions about
 * the current state.
 */
trait StateCache {
  def event(e: PlatformEvent): Task[Unit]
  def distribution(d: Distribution): Task[Unit]
  def plan(p: Plan): Task[Unit]
}

object MemoryStateCache extends StateCache {
  private[funnel] val history: BoundedStack[PlatformEvent] =
    new BoundedStack[PlatformEvent](2000)

  private[funnel] val distribution: Ref[Distribution] =
    new Ref(Distribution.empty)

  private[funnel] val plans: BoundedStack[Plan] =
    new BoundedStack[Plan](2000)

  def event(e: PlatformEvent): Task[Unit] =
    Task.delay(history.push(e))

  def distribution(d: Distribution): Task[Unit] =
    Task.delay(distribution.update(_ => d))

  def plan(p: Plan): Task[Unit] =
    Task.delay(plans.push(p))
}
