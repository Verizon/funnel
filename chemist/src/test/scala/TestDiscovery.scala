package funnel
package chemist

import scalaz.concurrent.Task

class TestDiscovery extends Discovery {
  def listActiveFlasks: Task[Seq[Flask]] = ???
  def listAllFlasks: Task[Seq[Flask]] = ???
  def listTargets: Task[Seq[(TargetID, Set[Target])]] = ???
  def listUnmonitorableTargets: Task[Seq[(TargetID, Set[Target])]] = ???
  def lookupFlask(id: funnel.chemist.FlaskID): Task[Flask] = ???
  def lookupTarget(id: funnel.chemist.TargetID): Task[Seq[Target]] = ???
  def lookupTargets(id: funnel.chemist.TargetID): Task[Set[Target]] = ???
}
