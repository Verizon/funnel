package funnel
package chemist

import scalaz.concurrent.Task
import Sharding.Distribution
import scalaz.==>>
import scalaz.std.string._
import scalaz.syntax.applicative._
import scalaz.stream.{Sink, Process, async}
import async.mutable.Signal
import java.net.URI
import messages.Error
import messages.Telemetry._


trait Repository {

  type Cluster   = String
  type InstanceM = InstanceID ==>> Instance
  type KeyM      = Cluster    ==>> Set[Key[Any]]

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
import funnel.internals._
import journal.Logger

class StatefulRepository(discovery: Discovery, signal: Signal[Boolean]) extends Repository {
  private val log = Logger[StatefulRepository]

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

  /**
   * stores the set of all the keys we have ever seen
   */
  private val K = new Ref[Set[Key[Any]]](Set.empty)

  /**
   * remember what the last set of keys looked like from this flask we
   * need this to diff with the next set so that we know if we are
   * adding or removing keys
   */
  private val LK = new Ref[InstanceId ==>> Set[Key[Any]]](==>>.empty)

  /**
   * stores the list of errors we have gotten from flasks, most recent
   * first.
   */
  private val E = new BoundedStack[Error](100)

  /////////////// audit operations //////////////////

  def addEvent(e: AutoScalingEvent): Task[Unit] = {
    log.info(s"Adding auto-scalling event to the historical events: $e")
    Q.push(e)
  }

  def historicalEvents: Task[Seq[AutoScalingEvent]] =
    Task.delay(Q.toSeq.toList)

  private val keySink: Sink[Task,(InstanceId, Set[Key[Any]])] =
    Process.constant {
      case (id, keys) =>
        val old: Set[Key[Any]] = LK.get(_.lookup(id)) getOrElse Set.empty
        val removed = old - keys
        val added = keys - old

        // for the removed keys, we have to see if they are being handled by some other flask, otherwise, remove them
        removed foreach { k =>
          cluster = k.
        }

    }

    Task.delay(K.update(keys ++ _)))

  private val errorSink: Sink[Task,Error] = Process.constant(E.push(_))

  /////////////// instance operations ///////////////

  def addInstance(instance: Instance): Task[InstanceM] =
    Task.delay(I.update(_.insert(instance.id, instance))) <* Task.delay {
      val (keysout, errorsS, sub) = telemetrySubscribeSocket(instance.telemetryLocation.asURI(), signal)
      (keysout.discrete to keySink).run.runAsync(_ => ()) // STU do something with the errors
      (errorsS to errorSink).run.runAsync(_ => ()) // STU do something with the errors
    }

  def removeInstance(id: InstanceID): Task[InstanceM] =
    Task.delay(I.update(_.delete(id)))

  def instance(id: InstanceID): Task[Instance] =
    I.get.lookup(id) match {
      case None    => Task.fail(InstanceNotFoundException(id))
      case Some(i) => Task.now(i)
    }

  /////////////// flask operations ///////////////

  def distribution: Task[Distribution] =
    Task.now(D.get)

  def mergeDistribution(d: Distribution): Task[Distribution] =
    Task.delay(D.update(old =>
                 Distribution(old.byFlask.unionWith(d.byFlask)(_ ++ _),
                              old.byTarget.unionWith(d.byTarget)(_ ++ _))))

  def assignedTargets(id: InstanceID): Task[Set[Sharding.Target]] =
    D.get.byFlask.lookup(id) match {
      case None => Task.fail(InstanceNotFoundException(id, "Flask"))
      case Some(t) => Task.now(t)
    }

  /**
   * when a new flask comes online, we want to add that flask to the in-memory
   */
  def increaseCapacity(instanceId: InstanceID): Task[(Distribution,InstanceM)] = {
    log.debug(s"increaseCapacity instanceId=$instanceId")
    for {
      i <- discovery.lookupOne(instanceId)
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
      a <- Task.delay(D.update(_.addFlask(instance.id)))
      _  = log.debug(s"increaseCapacity, updating the distribution for ${instance.id}")
      b <- addInstance(instance)
      _  = log.debug(s"increaseCapacity, updated with ${instance.id}")
    } yield (a,b)

  def decreaseCapacity(downed: InstanceID): Task[Distribution] =
    for {
      _ <- removeInstance(downed)
      d <- Task.delay(D.update(_.deleteFlask(downed)))
    } yield d
}
