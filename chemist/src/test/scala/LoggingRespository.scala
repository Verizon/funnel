package funnel
package chemist

import java.util.concurrent.atomic.AtomicInteger
import scalaz.==>>
import scalaz.concurrent.Task
import funnel.internals._
import scalaz.std.string._
import scalaz.stream.async
import java.net.URI

class LoggingRepository extends Repository {
  import TargetLifecycle._
  import TargetState._

  private val all = new Ref[InstanceM](==>>())

  /////////////// audit operations //////////////////

  def addEvent(e: AutoScalingEvent): Task[Unit] = Task.now(())
  def historicalEvents: Task[Seq[RepoEvent]] = Task.now(Seq())
  def errors: Task[Seq[Error]] = Task.now(Seq())
  def keySink(uri: URI, keys: Set[Key[Any]]): Task[Unit] = Task.now(())
  def errorSink(e: Error): Task[Unit] = Task.now(())

  /////////////// instance operations ///////////////
  def platformHandler(a: PlatformEvent): Task[Unit] = ???

  def targetState(instanceId: URI): TargetState = Unknown

  def instance(id: URI): Option[Target] = all.get.lookup(id).map(_.msg.target)

  def countTask[A](i: AtomicInteger, t: Task[A]): Task[A] = t.onFinish { _ =>
    Task { val _ = i.incrementAndGet }
  }

  val increase = new AtomicInteger(0)

  val lifecycleQ: async.mutable.Queue[RepoEvent] = async.unboundedQueue

  /////////////// flask operations ///////////////
  import Sharding.Distribution
  def distribution: Task[Distribution] = countTask(increase, Task.now(Distribution.empty))
  def mergeDistribution(d: Distribution): Task[Distribution] = Task.now(Distribution.empty)
  def assignedTargets(flask: Flask): Task[Set[Target]] = Task.now(Set.empty)
}
