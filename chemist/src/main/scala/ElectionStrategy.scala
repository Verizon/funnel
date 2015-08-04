package funnel
package chemist

import scalaz.concurrent.Task

/**
 * Chemist is typically consuming event notificaitons about the state of
 * the external world (e.g. from messages queues, or a 3rd party stream),
 * and subsequetnly oft is the case where one will want to specifically
 * only have a single chemist node in the cluster actually consuming that
 * mutable, external resource. Likewise, assigning work to Flask instances
 * (critically important to avoid duplication of work assignments) should
 * only be conducted by a single node at a time.
 *
 * Another important use case is upgrades and system evolution. Over
 * time it is likley that you will want to migrate from one chemist to
 * a newer version, and as such, you only want one "cluster" of chemists
 * to be controlling your fleet of Flasks.
 *
 * With these use cases in mind, Chemist has the concept of an election
 * strategy, which provides a course grained mechinhism for deciding if
 * this machine / cluster is the leader or not.
 */
trait ElectionStrategy {
  def discovery: Discovery
  def leader: Task[Option[Location]]
  def isLeader(l: Location): Task[Boolean]
}

/**
 * A common use case is to run a single Chemist under process supervision
 * and is the mode in which most of the testing is done (e.g. the integration
 * test module). In these scenarios it make sense to use a singleton chemist
 * and always have a lone nominee that gets elected in perpetuity.
 */
case class ForegoneConclusion(
  discovery: Discovery,
  nominee: Location
) extends ElectionStrategy {
  val leader: Task[Option[Location]] = Task.now(Some(nominee))
  def isLeader(l: Location): Task[Boolean] = Task.now(true)
}

/**
 * FirstFoundActive stratagy is one that makes no real choices of its own,
 * rather, control comes from a third-party agent which determines if
 * this chemist is the one - true - chemist that will commendere all the
 * flasks, live-long and prosper (until the next deployment, at least).
 *
 * In orcer to effectivly use this stratagy, you need to plugin
 * an appropriate `Classifier` into whatever `Discovery` instance you
 * supply to this class. If you're just using the `DefaultClassifier`
 * your results will likley be non-deterministic if you're running multiple
 * instnaces of chemist.
 */
case class FirstFoundActive(
  discovery: Discovery
) extends ElectionStrategy {

  def leader: Task[Option[Location]] =
    discovery.listActiveChemists.map(_.headOption)

  def isLeader(mine: Location): Task[Boolean] =
    leader.map {
      case Some(theirs) => mine == theirs
      case None         => true // their was no current leader, so take control
    }
}
