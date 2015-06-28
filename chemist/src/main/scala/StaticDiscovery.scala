package funnel
package chemist

import scalaz.concurrent.Task

class StaticDiscovery(targets: Map[TargetID, Set[Target]], flasks: Map[FlaskID, Flask]) extends Discovery {
  def listTargets: Task[Seq[(TargetID, Set[Target])]] = Task.delay(targets.toSeq)
  def listActiveFlasks: Task[Seq[Flask]] = Task.delay(flasks.values.toSeq)
  // TIM: im sorry, just trying to proove something out and cant fix this right now.
  def listActiveChemists: Task[Seq[Location]] = null
  def lookupFlask(id: FlaskID): Task[Flask] = Task.delay(flasks(id))	// Can obviously cause the Task to fail
  def lookupTargets(id: TargetID): Task[Set[Target]] = Task.delay(targets(id))	// Can obviously cause the Task to fail
}
