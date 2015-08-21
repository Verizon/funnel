package funnel
package chemist

import concurrent.duration._
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
import java.net.URI

object RepositorySpec extends Properties("StaticRepository") with ArbitraryLifecycle {
  import TargetLifecycle._
  import TargetState._
  import RepoEvent._

  def oneState(repo: StatefulRepository, id: URI): Boolean = {
    val numStates = repo.stateMaps.get.fold(0)((k,v,a) => a + v.lookup(id).fold(0)(_ => 1))
    numStates == 1
  }

  def consistentRepo(repo: StatefulRepository): Boolean = {
    val s: List[URI] = repo.targets.get.keys
    s.foldLeft(true)((b, i) => b && oneState(repo, i))
  }

  def matchesStates(repo: StatefulRepository, states: URI ==>> TargetState): Boolean = {
    states.keySet == repo.targets.get.keySet
  }

  implicit val arbitraryStateChange: Arbitrary[StateChange] = Arbitrary {
    for {
      from <- arbitrary[TargetState]
      to <- arbitrary[TargetState]
      msg <- arbitrary[TargetMessage]
    } yield StateChange(from,to,msg)
  }

  property("handle state changes") = forAll {(events: Vector[StateChange]) =>
    val repo = new StatefulRepository
    println("about to in")
    val in = (events.map(repo.processRepoEvent) :+ repo.repoCommandsQ.close).sequence_.map(_ => true).run
    println("about to out")
    val out = repo.repoCommands.run.map(_ => true).run

    in & out
  }

  case class CtTarget(instance: Target,
                      changes: Vector[(TargetState,Long)])
  case class ConsistencyTest(targets: Set[CtTarget])

  val ctGen: Gen[ConsistencyTest] = {
    val ct = arbitrary[Vector[Target]] flatMap { instances =>
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

  def targetMessage(i: Target, state: TargetState): TargetMessage = {
    state match {
      case Unknown =>
        Discovery(i, System.currentTimeMillis)
      case Unmonitored =>
        Discovery(i, System.currentTimeMillis)
      case Assigned =>
        Assignment(i, FlaskID(""), System.currentTimeMillis)
      case Monitored =>
        Confirmation(i, FlaskID(""), System.currentTimeMillis)
      case DoubleAssigned =>
        Assignment(i, FlaskID(""), System.currentTimeMillis)
      case Problematic =>
        Problem(i, FlaskID(""), "msg", System.currentTimeMillis)
      case DoubleMonitored =>
        Confirmation(i, FlaskID(""), System.currentTimeMillis)
      case Fin =>
        Terminated(i, System.currentTimeMillis)
      case Unmonitorable =>
        Terminated(i, System.currentTimeMillis)
      case Investigating =>
        Investigate(i, System.currentTimeMillis, 0)
    }
  }

  property("keep state consistent") = forAll(ctGen) {(ct: ConsistencyTest) =>
    val events: Vector[(Long, (Target,TargetState))] = for {
      t <- ct.targets.toVector
      c <- t.changes
    } yield c._2 -> (t.instance -> c._1)

    type S = URI ==>> TargetState

    val statechanges: ((Target,TargetState)) => State[S, RepoEvent.StateChange] = {
      case (i, to) =>
        for {
          m <- State.get[S]
          from = m.lookup(i.uri).getOrElse(Unknown)
          sc = StateChange(from, to, targetMessage(i, to))
          _ <-  State.put[S](m + (i.uri -> to))
        } yield sc
    }

    val init: S = ==>>.empty
    val (states, msgs) = events.sortBy(_._1).map(_._2).runTraverseS(init)(statechanges)

    val repo = new StatefulRepository
    val doRun = (msgs.map(repo.processRepoEvent) :+ repo.repoCommandsQ.close).sequence_.map(_ => true)
    val in = doRun.run
    val out = repo.repoCommands.run.map(_ => true).run
    in && out && consistentRepo(repo) && matchesStates(repo, states)
  }
}

