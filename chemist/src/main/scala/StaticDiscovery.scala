package funnel
package chemist

import scalaz.concurrent.Task

class StaticDiscovery(instances: Instance*) extends Discovery {
  def isFlask(i: Instance): Boolean = i.application.map(_.name.startsWith("flask")).getOrElse(false)
  def list: Task[Seq[Instance]] = Task(instances)
  def lookupMany(ids: Seq[InstanceID]): Task[Seq[Instance]] = Task(instances.filter(i => ids.contains(i.id)))
  def lookupOne(id: InstanceID): Task[Instance] = Task(instances.find(i => i.id == id).get)	// Can obviously cause the Task to fail
}
