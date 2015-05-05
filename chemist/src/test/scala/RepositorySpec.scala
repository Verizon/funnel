package funnel
package chemist

import org.scalacheck._
import Arbitrary._
import org.scalacheck.Prop.forAll
import shapeless.contrib.scalacheck._
import scalaz.{State, ==>>}
import scalaz.stream.Process
import Process._
import scalaz.concurrent.Task
import scalaz.std.string._
import scalaz.std.vector._
import scalaz.syntax.traverse._
import scala.collection.JavaConversions._

object RespositorySpec extends Properties("StaticRepository") with ArbitraryLifecycle {
  import TargetLifecycle._
  import TargetState._

  def oneState(repo: StatefulRepository, id: InstanceID): Boolean = {
    val numStates = repo.stateMaps.get.fold(0)((k,v,a) => a + v.lookup(id).fold(0)(_ => 1))
    numStates == 1
  }

  def consistentRepo(repo: StatefulRepository): Boolean = {
    val s: List[InstanceID] = repo.all.get.keys
    s.foldLeft(true)((b, i) => b && oneState(repo, i))
  }

  def matchesStates(repo: StatefulRepository, states: InstanceID ==>> TargetState): Boolean = {
    states.keySet == repo.all.get.keySet
  }

  property("handle state changes") = forAll {(events: Seq[StateChange]) =>
    val repo = new StatefulRepository
    val in =  Process.eval(Task.now(events)).flatMap(Process.emitAll).to(repo.lifecycleS).run.map(_ => true).run
    repo.lifecycle(Seq.empty)
    repo.lifecycleQ.close.run
    val out = repo.repoCommands.run.map(_ => true).run

    in & out
  }

  case class CtTarget(instance: Instance,
                      changes: Vector[(TargetState,Long)])
  case class ConsistencyTest(targets: Set[CtTarget])

  val ctGen: Gen[ConsistencyTest] = {
    val ct = arbitrary[Vector[Instance]] flatMap { instances =>
      Gen.sequence[Vector, CtTarget] {
        instances.distinct.map { i =>
          for {
            c <- arbitrary[Vector[(TargetState,Long)]]
          } yield CtTarget(i, c)
        }
      }
    }
    ct map (ts => ConsistencyTest(ts.toSet))
  }

  def targetMessage(i: Instance, state: TargetState): TargetMessage = {
    state match {
      case Unknown =>
        Discovery(i, System.currentTimeMillis)
      case Unmonitored =>
        Discovery(i, System.currentTimeMillis)
      case Assigned =>
        Assignment(i, "", System.currentTimeMillis)
      case Monitored =>
        Confirmation(i, "", System.currentTimeMillis)
      case DoubleAssigned =>
        Assignment(i, "", System.currentTimeMillis)
      case DoubleMonitored =>
        Confirmation(i, "", System.currentTimeMillis)
      case Fin =>
        Terminated(i, System.currentTimeMillis)
    }
  }

  property("keep state consistent") = forAll(ctGen) {(ct: ConsistencyTest) =>
    val events: Vector[(Long, (Instance,TargetState))] = for {
      t <- ct.targets.toVector
      c <- t.changes
    } yield c._2 -> (t.instance -> c._1)

    type S = InstanceID ==>> TargetState

    val statechanges: ((Instance,TargetState)) => State[S, StateChange] = {
      case (i, to) =>
        for {
          m <- State.get[S]
          from = m.lookup(i.id).getOrElse(Unknown)
          sc = StateChange(from, to, targetMessage(i, to))
          _ <-  State.put[S](m + (i.id -> to))
        } yield sc
    }

    val init: S = ==>>.empty
    val (states, msgs) = events.sortBy(_._1).map(_._2).runTraverseS(init)(statechanges)

    val repo = new StatefulRepository
    val doRun = Process.eval(Task.now(msgs)).flatMap(Process.emitAll).to(repo.lifecycleS)
    val in = doRun.run.map(_ => true).run // da do run run
    repo.lifecycleQ.close.run
    repo.lifecycle(Seq.empty)
    val out = repo.repoCommands.run.map(_ => true).run
    in && out && consistentRepo(repo) && matchesStates(repo, states)
  }
}

