package funnel
package chemist

import scalaz.concurrent.Task

trait Discovery {
  def listTargets: Task[Seq[(TargetID,Set[Target])]]
  def listUnmonitorableTargets: Task[Seq[(TargetID,Set[Target])]]
  def listFlasks: Task[Seq[Flask]]
  def lookupTargets(id: TargetID): Task[Set[Target]]
  def lookupFlask(id: FlaskID): Task[Flask]
}
