package funnel
package chemist

import java.util.concurrent.atomic.AtomicInteger
import scalaz.==>>
import scalaz.concurrent.Strategy
import scalaz.concurrent.Task
import funnel.internals._
import scalaz.std.string._
import scalaz.stream.{Process,async}
import java.net.URI

class LoggingRepository extends Repository {
  import TargetLifecycle._
  import TargetState._

  private val all = new Ref[InstanceM](==>>())

  /////////////// audit operations //////////////////

  def addEvent(e: AutoScalingEvent): Task[Unit] = Task.now(())
  def errors: Task[Seq[Error]] = Task.now(Seq())
  def historicalPlatformEvents: Task[Seq[PlatformEvent]] = Task.now(Seq())
  def historicalRepoEvents: scalaz.concurrent.Task[Seq[funnel.chemist.RepoEvent]] = Task.now(Seq())
  def keySink(uri: URI, keys: Set[Key[Any]]): Task[Unit] = Task.now(())
  def errorSink(e: Error): Task[Unit] = Task.now(())
  def instances: Task[Seq[(URI, RepoEvent.StateChange)]] = Task.now(Seq())

  /////////////// instance operations ///////////////
  def platformHandler(a: PlatformEvent): Task[Unit] = ???

  def targetState(instanceId: URI): TargetState = Unknown

  def instance(id: URI): Option[Target] = all.get.lookup(id).map(_.msg.target)

  def countTask[A](i: AtomicInteger, t: Task[A]): Task[A] = t.onFinish { _ =>
    Task.delay { val _ = i.incrementAndGet }
  }

  def assignedTargets(flask: funnel.chemist.FlaskID): scalaz.concurrent.Task[Set[funnel.chemist.Target]] = ???
  def flask(id: funnel.chemist.FlaskID): Option[funnel.chemist.Flask] = ???
  def unassignedTargets: scalaz.concurrent.Task[Set[funnel.chemist.Target]] = ???
  def unmonitorableTargets: scalaz.concurrent.Task[List[URI]] = ???

  val increase = new AtomicInteger(0)

  val lifecycleQ: async.mutable.Queue[RepoEvent] = async.unboundedQueue(Strategy.Executor(Chemist.serverPool))

  /////////////// flask operations ///////////////
  import Sharding.Distribution
  def distribution: Task[Distribution] = countTask(increase, Task.now(Distribution.empty))
  def mergeDistribution(d: Distribution): Task[Distribution] = Task.now(Distribution.empty)
  def mergeExistingDistribution(d: Distribution): Task[Distribution] = Task.now(Distribution.empty)
  def assignedTargets(flask: Flask): Task[Set[Target]] = Task.now(Set.empty)

  def repoCommands: Process[Task, RepoCommand] = ???

}
