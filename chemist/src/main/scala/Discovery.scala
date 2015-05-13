package funnel
package chemist

import scalaz.concurrent.Task

trait Discovery {
//  def list: Task[Seq[(TargetID,Set[Target])]]
  def listTargets: Task[Seq[(TargetID,Set[Target])]]
  def listFlasks: Task[Seq[Flask]]
//  def lookupMany(ids: Seq[InstanceID]): Task[Seq[Instance]]
  def isFlask(id: String): Task[Boolean]
  def lookupTargets(id: TargetID): Task[Set[Target]]
  def lookupFlask(id: FlaskID): Task[Flask]
}
