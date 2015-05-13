package funnel
package chemist

import scalaz.concurrent.Task

class TestDiscovery extends Discovery {
  def listFlasks: scalaz.concurrent.Task[Seq[funnel.chemist.Flask]] = ???
  def listTargets: scalaz.concurrent.Task[Seq[(funnel.chemist.TargetID, Set[funnel.chemist.Target])]] = ???
  def lookupFlask(id: funnel.chemist.FlaskID): scalaz.concurrent.Task[funnel.chemist.Flask] = ???
  def lookupTarget(id: funnel.chemist.TargetID): scalaz.concurrent.Task[Seq[funnel.chemist.Target]] = ???
  def isFlask(id: String): scalaz.concurrent.Task[Boolean] = ???
  def lookupTargets(id: funnel.chemist.TargetID): scalaz.concurrent.Task[Set[funnel.chemist.Target]] = ???
}
