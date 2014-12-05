package oncue.svc.funnel.chemist

import scalaz.concurrent.Task
import Sharding.Distribution
import scalaz.==>>

trait Repository {

  type InstanceM   = InstanceID ==>> Instance

  /////////////// audit operations //////////////////

  def addEvent(e: AutoScalingEvent): Task[Unit]
  def historicalEvents: Task[Seq[AutoScalingEvent]]

  /////////////// instance operations ///////////////

  def addInstance(instance: Instance): Task[InstanceM]
  def removeInstance(id: InstanceID): Task[InstanceM]
  def instance(id: InstanceID): Task[Instance]

  /////////////// flask operations ///////////////

  def distribution: Task[Distribution]
  def mergeDistribution(d: Distribution): Task[Distribution]
  def assignedTargets(flask: InstanceID): Task[Set[Sharding.Target]]
  def increaseCapacity(instanceId: InstanceID): Task[(Distribution,InstanceM)]
  def increaseCapacity(instance: Instance): Task[(Distribution,InstanceM)]
  def decreaseCapacity(downed: InstanceID): Task[Distribution]
  def isFlask(id: InstanceID): Task[Boolean] =
    instance(id).map(_.tags.get("type"
      ).map(_.startsWith("flask")
      ).getOrElse(false))
}

import com.amazonaws.services.ec2.AmazonEC2
import intelmedia.ws.funnel.internals._
import journal.Logger

case class MissingInstanceException(override val getMessage: String) extends RuntimeException(getMessage)

class StatefulRepository(ec2: AmazonEC2) extends Repository {
  private lazy val log = Logger[StatefulRepository]

  /**
   * stores the mapping between flasks and their assigned workload
   */
  private val D = new Ref[Distribution](Distribution.empty)

  /**
   * stores a key-value map of instance-id -> host
   */
  private val I = new Ref[InstanceM](==>>())

  /**
   * stores lifecycle events to serve as an audit log that
   * retains the last 100 scalling events
   */
  private val Q = new BoundedStack[AutoScalingEvent](100)

  /////////////// audit operations //////////////////

  def addEvent(e: AutoScalingEvent): Task[Unit] =
    Task(Q.push(e))

  def historicalEvents: Task[Seq[AutoScalingEvent]] =
    Task(Q.toSeq)

  /////////////// instance operations ///////////////

  def addInstance(instance: Instance): Task[InstanceM] =
    Task(I.update(_.insert(instance.id, instance)))

  def removeInstance(id: InstanceID): Task[InstanceM] =
    Task(I.update(_.delete(id)))

  def instance(id: InstanceID): Task[Instance] =
    I.get.lookup(id) match {
      case None    => Task.fail(MissingInstanceException(s"No instance with the ID $id"))
      case Some(i) => Task.now(i)
    }

  /////////////// flask operations ///////////////

  def distribution: Task[Distribution] =
    Task.now(D.get)

  def mergeDistribution(d: Distribution): Task[Distribution] =
    Task(D.update(_.unionWith(d)(_ ++ _)))

  def assignedTargets(flask: InstanceID): Task[Set[Sharding.Target]] =
    D.get.lookup(flask) match {
      case None => Task.fail(MissingInstanceException(s"No flask with the ID $flask"))
      case Some(t) => Task.now(t)
    }

  /**
   * when a new flask comes online, we want to add that flask to the in-memory
   */
  def increaseCapacity(instanceId: InstanceID): Task[(Distribution,InstanceM)] = {
    log.debug(s"increaseCapacity instanceId=$instanceId")
    for {
      i <- Deployed.lookupOne(instanceId)(ec2)
      a <- increaseCapacity(i)
    } yield a
  }

  /**
   * when a new flask comes online, we want to add that flask to the in-memory.
   * 1. Add a new instance key and set its workload to an empty set
   * 2. Add that instance to the running set of all known machine instances
   */
  def increaseCapacity(instance: Instance): Task[(Distribution,InstanceM)] =
    for {
      a <- Task(D.update(_.insert(instance.id, Set.empty)))
      _  = log.debug(s"increaseCapacity, updating the distribution for ${instance.id}")
      b <- addInstance(instance)
      _  = log.debug(s"increaseCapacity, updated with ${instance.id}")
    } yield (a,b)

  def decreaseCapacity(downed: InstanceID): Task[Distribution] =
    for {
      _ <- removeInstance(downed)
      d <- Task(D.update(_.delete(downed)))
    } yield d
}
