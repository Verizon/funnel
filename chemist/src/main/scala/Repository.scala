package funnel
package chemist

import scalaz.concurrent.Task
import Sharding.Distribution
import scalaz.{-\/,==>>}
import scalaz.std.string._
import scalaz.syntax.monad._
import scalaz.stream.{Sink, Channel, Process, Process1, async}
import java.net.URI
import TargetLifecycle._

/**
 * A Repository acts as our ledger of our current view of the state of
 * the world.  This includes Flasks which are able to monitor Targets,
 * and a list of Targets to be monitored, including the Flasks themsevles.
 */
trait Repository {
  import RepoEvent._
  /**
   * Maps IDs to the Instances, and the details of their last state change
   */
  type InstanceM = URI        ==>> StateChange
  type FlaskM    = FlaskID    ==>> Flask

  /**
   * for any possible state of a target, a map of instances in that state
   */
  type StateM    = TargetState ==>> InstanceM

  /////////////// audit operations //////////////////

  /**
   * The most recent state changes
   */
  def historicalEvents: Task[Seq[RepoEvent]]

  /**
   * the most recent mirroring errors
   */
  def errors: Task[Seq[Error]]

  def keySink(uri: URI, keys: Set[Key[Any]]): Task[Unit]

  def errorSink(e: Error): Task[Unit]

  /////////////// instance operations ///////////////

  def targetState(instanceId: URI): TargetState

//  def target(id: InstanceID): Task[Instance]
//  def flask(id: InstanceID): Task[Instance]
  def instance(id: URI): Option[Target]

  val lifecycleQ: async.mutable.Queue[RepoEvent]

  /////////////// flask operations ///////////////

  def distribution: Task[Distribution]
  def mergeDistribution(d: Distribution): Task[Distribution]
  def assignedTargets(flask: Flask): Task[Set[Target]]

//  def isFlask(id: InstanceID): Task[Boolean] = flask(id).attempt.map(_.isRight)

//  def subscribe(events: Process[Task,TargetMessage]): Unit
}

import com.amazonaws.services.ec2.AmazonEC2
import funnel.internals._
import journal.Logger

class StatefulRepository/*(discovery: Discovery)*/ extends Repository {
  import RepoEvent._
  import TargetState._
  private val log = Logger[StatefulRepository]

  /**
   * stores the mapping between flasks and their assigned workload
   */
  private val D = new Ref[Distribution](Distribution.empty)

  /**
   * stores a key-value map of instance-id -> host
   */
  val targets = new Ref[InstanceM](==>>.empty)
  val flasks  = new Ref[FlaskM](==>>.empty)

/*
  def target(id: InstanceID): Task[Instance] =
    targets.get.lookup(id) match {
      case None    => Task.fail(new IllegalArgumentException(s"No target with the ID $id"))
      case Some(i) => Task.now(i.msg.instance)
    }

  def flask(id: InstanceID): Task[Instance] =
    flasks.get.lookup(id) match {
      case None    => Task.fail(new IllegalArgumentException(s"No flask with the ID $id"))
      case Some(i) => Task.now(i)
    }
 */
  private val emptyMap: InstanceM = ==>>.empty
  val stateMaps = new Ref[StateM](==>>(Unknown -> emptyMap,
                                       Unmonitored -> emptyMap,
                                       Assigned -> emptyMap,
                                       Monitored -> emptyMap,
                                       DoubleAssigned -> emptyMap,
                                       DoubleMonitored -> emptyMap,
                                       Fin -> emptyMap))

  /**
   * stores lifecycle events to serve as an audit log that
   * retains the last 100 scalling events
   */
  private[chemist] val historyStack = new BoundedStack[RepoEvent](100)

  /**
   * stores the list of errors we have gotten from flasks, most recent
   * first.
   */
  private[chemist] val errorStack = new BoundedStack[Error](100)

  /////////////// audit operations //////////////////

  /*  def addEvent(e: AutoScalingEvent): Task[Unit] = {
   log.info(s"Adding auto-scalling event to the historical events: $e")
   Q.push(e)
   }
   */
  def historicalEvents: Task[Seq[RepoEvent]] =
    Task.delay(historyStack.toSeq.toList)

  def errors: Task[Seq[Error]] =
    Task.delay(errorStack.toSeq.toList)

   def keySink(uri: URI, keys: Set[Key[Any]]): Task[Unit] = Task.now(())

  def errorSink(e: Error): Task[Unit] = errorStack.push(e)

  /////////////// target lifecycle ///////////////

  // inbound events from TargetLifecycle
  val lifecycleQ: async.mutable.Queue[RepoEvent] = async.unboundedQueue

  // outbound events to be consumed by Sharding
  private val repoCommandsQ: async.mutable.Queue[RepoCommand] = async.unboundedQueue
  val repoCommands: Process[Task, RepoCommand] = repoCommandsQ.dequeue

  def lifecycle(): Unit =  {
    val go: RepoEvent => Process[Task, RepoCommand] = { re =>
      Process.eval(historyStack.push(re)).flatMap{ _ =>
        re match {
          case sc @ StateChange(from,to,msg) =>
            val id = msg.target.uri
            targets.update(_.insert(id, sc))
            stateMaps.update(_.update(from, (m => Some(m.delete(id)))))
            stateMaps.update(_.update(to, (m => Some(m.insert(id, sc)))))
            sc.to match {
              case Unknown =>
                Process.emit(RepoCommand.Monitor(sc.msg.target))
              case _ => Process.halt
            }
          case NewFlask(flask) =>

            flasks.get + (flask.id -> flask)
            Process.halt

          case RepoEvent.TerminatedFlask(id) =>
            flasks.get.lookup(id) match {
              case Some(_) =>
                flasks.update(_.delete(id))
                Process.emit(RepoCommand.ReassignWork(id))
              case None => Process.halt
            }

          case RepoEvent.TerminatedTarget(id) =>
            targets.get.lookup(id) match {
              case Some(i) =>
                targets.update(_.delete(id))
                stateMaps.update(_.update(i.to, (m => Some(m.delete(i.msg.target.uri)))))
                Process.halt
              case None => Process.halt
            }
        }
      }
    }

    (lifecycleQ.dequeue flatMap go to repoCommandsQ.enqueue).run.runAsync {
      case -\/(e) =>
        log.error("error consuming lifecycle events", e)
        repoCommandsQ.close.run
      case _ =>
        log.info("lifecycle events stream finished")
        repoCommandsQ.close.run
    }
  }

  /**
   * determine the current perceived state of a Target
   */
  def targetState(id: URI): TargetState = targets.get.lookup(id).fold[TargetState](TargetState.Unknown)(_.to)
  def instance(id: URI): Option[Target] = targets.get.lookup(id).map(_.msg.target)

  /////////////// flask operations ///////////////

  def distribution: Task[Distribution] = Task.now(D.get)

  def mergeDistribution(d: Distribution): Task[Distribution] =
    Task.delay(D.update(_.unionWith(d)(_ ++ _)))

  def assignedTargets(flask: Flask): Task[Set[Target]] =
    D.get.lookup(flask) match {
      case None => Task.fail(InstanceNotFoundException(flask.id.value, "Flask"))
      case Some(t) => Task.now(t)
    }
}
