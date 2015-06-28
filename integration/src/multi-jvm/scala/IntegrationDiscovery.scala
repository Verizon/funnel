package funnel
package integration

import scalaz.concurrent.Task
import chemist.{FlaskID,Flask,TargetID,Target,Discovery,Location}
import java.util.UUID.randomUUID

class IntegrationDiscovery extends Discovery {
  def listActiveFlasks: Task[Seq[Flask]] =
    Task.now(IntegrationFixtures.flask1 :: Nil)

  private val randomids: Map[TargetID, Set[Target]] =
    IntegrationFixtures.targets.map(t =>
      TargetID(randomUUID.toString) -> Set(t)
    ).toMap

  def listTargets: Task[Seq[(TargetID, Set[Target])]] =
    Task.now(randomids.toSeq)

  def lookupFlask(id: FlaskID): Task[Flask] =
    for {
      a <- listActiveFlasks
    } yield a.find(_.id == id).getOrElse(sys.error("No flask found with that ID."))

  def lookupTarget(id: TargetID): Task[Seq[Target]] =
    Task.now(randomids.get(id).map(_.toSeq).toSeq.flatten)

  def lookupTargets(id: TargetID): Task[Set[Target]] =
    Task.now(randomids.get(id).toSet.flatten)
}
