package funnel
package chemist

import scalaz.concurrent.Task

trait Discovery {
  def list: Task[Seq[Instance]]
  def lookupOne(id: InstanceID): Task[Instance]
  def lookupMany(ids: Seq[InstanceID]): Task[Seq[Instance]]
  def isFlask(i: Instance): Boolean
}
