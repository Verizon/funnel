package funnel
package chemist

import scalaz.concurrent.Task

class StaticDiscovery(targets: Map[TargetID, Set[Target]], flasks: Map[FlaskID, Flask]) extends Discovery {
//  def isFlask(id: String): Task[Boolean] = Task.now(flasks.get(FlaskID(id)).isDefined)
  def listTargets: Task[Seq[(TargetID, Set[Target])]] = Task(targets.toSeq)
  def listFlasks: Task[Seq[Flask]] = Task(flasks.values.toSeq)
//  def lookupMany(ids: Seq[InstanceID]): Task[Seq[Instance]] = Task(instances.filter(i => ids.contains(i.id)))
  def lookupFlask(id: FlaskID): Task[Flask] = Task(flasks(id))	// Can obviously cause the Task to fail
  def lookupTargets(id: TargetID): Task[Set[Target]] = Task(targets(id))	// Can obviously cause the Task to fail
}
