package funnel
package chemist

import java.util.concurrent.atomic.AtomicInteger
import scalaz.==>>
import scalaz.concurrent.Task
import funnel.internals._
import scalaz.std.string._
import messages.Error

class LoggingRepository extends Repository {
  import TargetLifecycle._
  import TargetState._

  private val all = new Ref[InstanceM](==>>())

  /////////////// audit operations //////////////////

  def addEvent(e: AutoScalingEvent): Task[Unit] = Task.now(())
  def historicalEvents: Task[Seq[StateChange]] = Task.now(Seq())
  def errors: Task[Seq[Error]] = Task.now(Seq())
  def keySink(flask: InstanceID, keys: Set[Key[Any]]): Task[Unit] = Task.now(())
  def errorSink(e: Error): Task[Unit] = Task.now(())

  /////////////// instance operations ///////////////

  def targetState(instanceId: InstanceID): TargetState = Unknown

  def instance(id: InstanceID): Task[Instance] =
    all.get.lookup(id) match {
      case None    => Task.fail(InstanceNotFoundException(id))
      case Some(i) => Task.now(i.msg.instance)
    }

  def countTask[A](i: AtomicInteger, t: Task[A]): Task[A] = t.onFinish { _ =>
    Task { val _ = i.incrementAndGet }
  }

  val increase = new AtomicInteger(0)

  /////////////// flask operations ///////////////
  import Sharding.Distribution
  def distribution: Task[Distribution] = countTask(increase, Task.now(Distribution.empty))
  def mergeDistribution(d: Distribution): Task[Distribution] = Task.now(Distribution.empty)
  def assignedTargets(flask: InstanceID): Task[Set[Target]] = Task.now(Set.empty)
  def increaseCapacity(instanceId: InstanceID): Task[(Distribution,InstanceM)] = countTask(increase,Task.now((Distribution.empty, ==>>())))
  def increaseCapacity(instance: Instance): Task[(Distribution,InstanceM)] = countTask(increase, Task.now((Distribution.empty, ==>>())))
  def decreaseCapacity(downed: InstanceID): Task[Distribution] = Task.now(Distribution.empty)
  override def isFlask(id: InstanceID): Task[Boolean] =
    instance(id).map(_.tags.get("type"
      ).map(_.startsWith("flask")
      ).getOrElse(false)).handle { case t => false }
}
