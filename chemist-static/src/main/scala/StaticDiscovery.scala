package funnel
package chemist

import scalaz.concurrent.Task

class StaticDiscovery(targets: Map[TargetID, Set[Target]], flasks: Map[FlaskID, Flask]) extends Discovery {
  def listTargets: Task[Seq[(TargetID, Set[Target])]] = Task.delay(targets.toSeq)
  def listUnmonitorableTargets: Task[Seq[(TargetID, Set[Target])]] = Task.now(Seq.empty)
  def listAllFlasks: Task[Seq[Flask]] = Task.delay(flasks.values.toSeq)
  def listActiveFlasks: Task[Seq[Flask]] = Task.delay(flasks.values.toSeq)
  def lookupFlask(id: FlaskID): Task[Flask] = Task.delay(flasks(id))	// Can obviously cause the Task to fail
  def lookupTargets(id: TargetID): Task[Set[Target]] = Task.delay(targets(id))	// Can obviously cause the Task to fail
}
