package funnel
package chemist

import Sharding.Distribution
import scalaz.concurrent.Task
/**
 * Represents the set of effects that will be applied to a given input
 */
sealed trait Plan {
  def task: Task[Unit]
}

/**
 * Actually distribute the work to the flasks
 */
case class Distribute(work: Distribution) extends Plan {
  val task: Task[Unit] = Task.now(())
}

/**
 * No effects to execute here, just bottom out with Unit
 */
case object Ignore extends Plan {
  val task: Task[Unit] = Task.now(())
}
