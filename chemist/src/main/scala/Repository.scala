package funnel
package chemist

import scalaz.concurrent.Strategy
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
  def historicalPlatformEvents: Task[Seq[PlatformEvent]]
  def historicalRepoEvents: Task[Seq[RepoEvent]]

  /**
   * the most recent mirroring errors
   */
  def errors: Task[Seq[Error]]
  def keySink(uri: URI, keys: Set[Key[Any]]): Task[Unit]
  def errorSink(e: Error): Task[Unit]
  def platformHandler(a: PlatformEvent): Task[Unit]
  /////////////// instance operations ///////////////

  def targetState(instanceId: URI): TargetState
  def instance(id: URI): Option[Target]
  def flask(id: FlaskID): Option[Flask]
  val lifecycleQ: async.mutable.Queue[RepoEvent]
  def instances: Task[Seq[(URI, StateChange)]]

  /////////////// flask operations ///////////////

  def distribution: Task[Distribution]
  def mergeDistribution(d: Distribution): Task[Distribution]
  def mergeExistingDistribution(d: Distribution): Task[Distribution]
  def assignedTargets(flask: FlaskID): Task[Set[Target]]
  def unassignedTargets: Task[Set[Target]]

  def repoCommands: Process[Task, RepoCommand]
}

import com.amazonaws.services.ec2.AmazonEC2
import funnel.internals._
import journal.Logger

class StatefulRepository extends Repository {
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

  private val emptyMap: InstanceM = ==>>.empty
  val stateMaps = new Ref[StateM](==>>(Unknown -> emptyMap,
                                       Unmonitored -> emptyMap,
                                       Assigned -> emptyMap,
                                       Monitored -> emptyMap,
                                       Problematic -> emptyMap,
                                       DoubleAssigned -> emptyMap,
                                       DoubleMonitored -> emptyMap,
                                       Fin -> emptyMap))

  /**
   * stores lifecycle events to serve as an audit log that
   * retains the last 100 scalling events
   */
  private[chemist] val historyStack = new BoundedStack[PlatformEvent](100)
  private[chemist] val repoHistoryStack = new BoundedStack[RepoEvent](100)

  /**
   * stores the list of errors we have gotten from flasks, most recent
   * first.
   */
  private[chemist] val errorStack = new BoundedStack[Error](100)

  /////////////// audit operations //////////////////

  def historicalPlatformEvents: Task[Seq[PlatformEvent]] =
    Task.delay(historyStack.toSeq.toList)

  def historicalRepoEvents: Task[Seq[RepoEvent]] =
    Task.delay(repoHistoryStack.toSeq.toList)

  def errors: Task[Seq[Error]] =
    Task.delay(errorStack.toSeq.toList)

  def keySink(uri: URI, keys: Set[Key[Any]]): Task[Unit] = Task.now(())

  def errorSink(e: Error): Task[Unit] = errorStack.push(e)

  /////////////// instance operations ///////////////
  def instances: Task[Seq[(URI, StateChange)]] =
    Task(targets.get.toList)

  def unassignedTargets: Task[Set[Target]] =
    Task(stateMaps.get.lookup(TargetState.Unmonitored).fold(Set.empty[Target])(m => m.values.map(_.msg.target).toSet))(Chemist.serverPool)

  def assignedTargets(flask: FlaskID): Task[Set[Target]] =
    D.get.lookup(flask) match {
      case None => Task.fail(InstanceNotFoundException(flask.value, "Flask"))
      case Some(t) => Task.now(t)
    }

  /////////////// target lifecycle ///////////////

  /**
   * Handle the Actions emitted from the Platform
   */
  def platformHandler(a: PlatformEvent): Task[Unit] = {
    historyStack.push(a).flatMap{ _ =>
      val lifecycle = TargetLifecycle.process(this) _
      a match {
        case PlatformEvent.NewTarget(target) =>
          Task.delay(log.info("platformHandler -- new target: " + target)) >>
          lifecycle(TargetLifecycle.Discovery(target, System.currentTimeMillis), targetState(target.uri))

        case PlatformEvent.NewFlask(f) =>
          Task.delay(log.info("platformHandler -- new task: " + f)) >>
          Task {
            D.update(_.insert(f.id, Set.empty))
            flasks.update(_.insert(f.id, f))
          }(Chemist.serverPool) >>
          repoCommandsQ.enqueueOne(RepoCommand.Telemetry(f)) >>
          repoCommandsQ.enqueueOne(RepoCommand.AssignWork(f))

        case PlatformEvent.TerminatedFlask(i) =>
          // This one is a little weird, we are enqueueing this to ourseles
          // we should probably eliminate this re-enqueing
          Task.delay(log.info("platformHandler -- terminated flask: " + i)) >>
          repoCommandsQ.enqueueOne(RepoCommand.ReassignWork(i))

        case PlatformEvent.TerminatedTarget(i) => {
          val target = targets.get.lookup(i)
          target.map { t =>
            Task {
              targets.update(_.delete(i))
              stateMaps.update(_.update(t.to, m => Some(m.delete(i))))
              ()
            }(Chemist.serverPool)
          }.getOrElse(Task.now(()))
        }
        case PlatformEvent.Monitored(f, i) =>
          // TODO: what is this was unexpected? then the lifecycle call will result in nothing
          log.info(s"platformHandler -- $i monitored by $f")
          val target = targets.get.lookup(i)
          target.map { t =>
            lifecycle(TargetLifecycle.Confirmation(t.msg.target, f, System.currentTimeMillis), t.to)
          } getOrElse Task.now(())

        case PlatformEvent.Unmonitored(f, i) => {
          log.info(s"platformHandler -- $i no longer monitored by by $f")
          val target = targets.get.lookup(i)
          target.map { t =>
            // TODO: make sure we handle correctly all the cases where this might arrive (possibly unexpectedly)
            lifecycle(TargetLifecycle.Unmonitoring(t.msg.target, f, System.currentTimeMillis), t.to)
          } getOrElse {
            // if we didn't even know about the target, what do we do? start monitoring it? nothing?
            Task.now(())
          }
        }
        case PlatformEvent.Problem(f, i, msg) => {
          log.error(s"platformHandler -- $i no exception from  $f: $msg")
          val target = targets.get.lookup(i)
          target.map { t =>
            // TODO: make sure we handle correctly all the cases where this might arrive (possibly unexpectedly)
            lifecycle(TargetLifecycle.Problem(t.msg.target, f, msg, System.currentTimeMillis), t.to)
          } getOrElse {
            // if we didn't even know about the target, what do we do? start monitoring it? nothing?
            Task.now(())
          }
        }
        case PlatformEvent.Assigned(fl, t) =>
          Task.delay(log.info(s"platformHandler -- $t assigned to $fl")) >>
          lifecycle(TargetLifecycle.Assignment(t, fl, System.currentTimeMillis), targetState(t.uri))
        case PlatformEvent.NoOp => Task.now(())
      }
    }
  }

  // inbound events from TargetLifecycle
  val lifecycleQ: async.mutable.Queue[RepoEvent] = async.unboundedQueue(Strategy.Executor(Chemist.serverPool))

  // outbound events to be consumed by Sharding
  private val repoCommandsQ: async.mutable.Queue[RepoCommand] = async.unboundedQueue(Strategy.Executor(Chemist.serverPool))
  val repoCommands: Process[Task, RepoCommand] = repoCommandsQ.dequeue

  def lifecycle(): Unit =  {
    val go: RepoEvent => Process[Task, RepoCommand] = { re =>
      Process.eval(repoHistoryStack.push(re)).flatMap{ _ =>
        log.info(s"lifecycle: $re")
        re match {
          case sc @ StateChange(from,to,msg) =>
            val id = msg.target.uri
            targets.update(_.insert(id, sc))
            stateMaps.update(_.update(from, (m => Some(m.delete(id)))))
            stateMaps.update(_.update(to, (m => Some(m.insert(id, sc)))))
            sc.to match {
              case Unmonitored =>
                Process.emit(RepoCommand.Monitor(sc.msg.target))

              // TODO when we implement flask transition we need to hanle double monitored stuff
              case _ => Process.halt
            }
          case NewFlask(flask) =>
            flasks.update(_ + (flask.id -> flask))
            Process.halt
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
  def flask(id: FlaskID): Option[Flask] = flasks.get.lookup(id)

  /////////////// flask operations ///////////////

  def distribution: Task[Distribution] = Task.now(D.get)

  def mergeDistribution(d: Distribution): Task[Distribution] = Task {
    D.update(_.unionWith(d)(_ ++ _))
  } (Chemist.serverPool)

  def mergeExistingDistribution(d: Distribution): Task[Distribution] = Task {
    d.toList.foreach {
      case (fl, targets) =>
        targets.foreach { t =>
          val sc = StateChange(TargetState.Unknown, TargetState.Monitored, Confirmation(t, fl, System.currentTimeMillis))
          stateMaps.update(_.map(_.delete(t.uri)))
          stateMaps.update(_.update(TargetState.Monitored, m => Some(m.insert(t.uri, sc))))
          this.targets.update(_.insert(t.uri, sc))
        }
    }
    D.update(_.unionWith(d)(_ ++ _))
  } (Chemist.serverPool)
}
