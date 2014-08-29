package oncue.svc.funnel.chemist

import scalaz.concurrent.Task
import Sharding.Distribution

trait Repository {

  /////////////// instance operations ///////////////

  def addInstance(instance: Instance): Task[InstanceM]
  def removeInstance(id: InstanceID): Task[InstanceM]

  /////////////// flask operations ///////////////

  def distribution: Task[Distribution]
  def increaseCapacity(instanceId: InstanceID): Task[(Distribution,InstanceM)]
  def increaseCapacity(instance: Instance): Task[(Distribution,InstanceM)]
  def decreaseCapacity(instanceId: InstanceID): Task[(Distribution,InstanceM)]
}

import scalaz.==>>
import com.amazonaws.services.ec2.AmazonEC2

case class MissingInstanceException(override val getMessage: String) extends RuntimeException(getMessage)

class ProductionRepository(ec2: AmazonEC2){
  /**
   * stores the mapping between flasks and their assigned workload
   */
  private val D = new Ref[Distribution](Distribution.empty)

  /**
   * stores a key-value map of instance-id -> host
   */
  private val I = new Ref[InstanceM](==>>())

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

  /**
   * when a new flask comes online, we want to add that flask to the in-memory
   */
  def increaseCapacity(instanceId: InstanceID): Task[(Distribution,InstanceM)] =
    for {
      i <- Deployed.lookupOne(instanceId)(ec2)
      a <- increaseCapacity(i)
    } yield a

  /**
   * when a new flask comes online, we want to add that flask to the in-memory.
   * 1. Add a new instance key and set its workload to an empty set
   * 2. Add that instance to the running set of all known machine instances
   */
  def increaseCapacity(instance: Instance): Task[(Distribution,InstanceM)] =
    for {
      a <- Task(D.update(_.insert(instance.id, Set.empty)))
      b <- addInstance(instance)
    } yield (a,b)

  def decreaseCapacity(instanceId: InstanceID): Task[Distribution] =
    for {
      _ <- removeInstance(instanceId)
      targets = D.get.lookup(instanceId)
      x <- Task.now(Sharding.distribution(targets)(D.get))
    } yield x
}


//   def decreaseCapacity(instance: Instance)(rd: Ref[Distribution], ri: Ref[InstanceM]): Task[(Distribution,InstanceM)] = {
//     // def redistribute(id: String) = {
//     //     val next = ref.update(_.delete(id))
//     //     val dd = Sharding.distribution(set)(ref.get) // this is kinda messy
//     //     ref.update(x => dd._2)
//     //     Sharding.distribute(dd)
//     //   }
//     // }
