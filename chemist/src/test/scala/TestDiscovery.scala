package funnel
package chemist

import scalaz.concurrent.Task

class TestDiscovery extends Discovery {
  def list: Task[Seq[Instance]] = ???
    //Task.now(Fixtures.instances)

  def lookupOne(id: InstanceID): Task[Instance] = ???
    //Task.now(Fixtures.instances.find)

  def lookupMany(ids: Seq[InstanceID]): Task[Seq[Instance]] = ???
    //Task.now(Fixtures.instances.filter(ids.contains(x.id)))

  def isFlask(i: Instance): Boolean = false
}
