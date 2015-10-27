package funnel
package chemist

import Sharding.Distribution
import scalaz.concurrent.Task
/**
 * Represents the set of effects that will be applied to a given input
 */
sealed trait Plan

/**
 * Actually distribute the work to the flasks
 */
case class Distribute(work: Distribution) extends Plan

/**
 *
 */
case class Redistribute(stop: Distribution, start: Distribution) extends Plan {
  import scalaz.std.set._

  /**
   * convenience for updating a specific flask shard with work to stop
   * and work to start. Typically this function assumes that the input
   * distributions were empty, as update append will use any existing
   * data and just shove the new stuff on the end, which might not be
   * what you want if the distributions are non-empty.
   */
  def update(shard: Flask, stopping: Set[Target], starting: Set[Target]): Redistribute =
    this.copy(
      stop  = stop.updateAppend(shard, stopping),
      start = start.updateAppend(shard, starting)
    )
}

object Redistribute {
  val empty = Redistribute(Distribution.empty, Distribution.empty)
}

/**
 * No effects to execute here, just bottom out with Unit
 */
case object Ignore extends Plan