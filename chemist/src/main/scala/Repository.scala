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

  /////////////// flask operations ///////////////

  def distribution: Task[Distribution] =
    Task.now(D.get)

  /**
   * when a new flask comes online, we want to add that flask to the in-memory
   */
  def increaseCapacity(instanceId: InstanceID): Task[(Distribution,InstanceM)] =
    for {
      i <- Deployed.lookupOne(instanceId)(ec2)
      a <- increaseCapacityWithInstance(i)
    } yield a

  /**
   * when a new flask comes online, we want to add that flask to the in-memory
   */
  def increaseCapacityWithInstance(instance: Instance): Task[(Distribution,InstanceM)] =
    for {
      a <- Task(D.update(_.insert(instance.id, Set.empty)))
      b <- addInstance(instance)
    } yield (a,b)

  def decreaseCapacity(instanceId: InstanceID): Task[(Distribution,InstanceM)] = ???
}

// object State {

//   def up(instance: Instance)(ri: Ref[InstanceM]): Task[InstanceM] =
//     Task.now(ri.update(_.insert(instance.id, instance)))

//   def down(id: InstanceID)(ri: Ref[InstanceM]): Task[InstanceM] =
//     Task.now(ri.update(_.delete(id)))

//   /**
//    * when a new flask comes online, we want to add that flask to the in-memory
//    */
//   def increaseCapacity(instance: Instance)(rd: Ref[Distribution], ri: Ref[InstanceM]): Task[(Distribution,InstanceM)] =
//     for {
//       a <- Task(rd.update(_.insert(instance.id, Set.empty)))
//       b <- up(instance)(ri)
//     } yield (a,b)

//   def decreaseCapacity(instance: Instance)(rd: Ref[Distribution], ri: Ref[InstanceM]): Task[(Distribution,InstanceM)] = {
//     // def redistribute(id: String) = {
//     //     val next = ref.update(_.delete(id))
//     //     val dd = Sharding.distribution(set)(ref.get) // this is kinda messy
//     //     ref.update(x => dd._2)
//     //     Sharding.distribute(dd)
//     //   }
//     // }

//     ???
//   }



// }