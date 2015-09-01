package funnel
package chemist

import quiver._

import scalaz.Order
import scalaz.std.anyVal._
import scalaz.concurrent.Task
import scalaz.stream.{async,Process,Process1}
import scalaz.stream.async.mutable.Queue
import journal.Logger

object TargetLifecycle {
/*
                   +===================+
                   |      Unknown      |
                   +===================+
                             |
                  (Discover) |           A new Instance is discovered
                             v
                   +-------------------+
                   |    Unmonitored    |
                   +-------------------|
                             |
                    (Assign) |          Chemist assigns the work to a Flask
                             v
                   +-------------------+
                   |     Assigned      |
                   +-------------------+
                             |
                   (Confirm) |          Flask confirms it is monitoring the Instance
                             v
                   +-------------------+
             +-----|     Monitored     |<---+               ---+
             |     +-------------------+    |                  |   Decommisioning a flask
             |               |              |                  |   ---------------------------
             |      (Assign) |              |                  |   We assign the instances from a Flask to
             |               v              |                  |   a new Flask, when the new Flask confirms
             |     +-------------------+    |                  |   that it is monitoring the Funnel, we
             |     |  Double Assigned  |    |                  |-- tell the old Flask to stop monitoring,
             |     +-------------------+    |                  |   when the old Flask confirms it has stopped
 (Unmonitor) |               |              |                  |   (Unmonitor) it goes back to the Monitored
             |     (Confirm) |              | (Unmonitor)      |   state.
             |               v              |                  |
             |     +-------------------+    |                  |
             |     |  Double Monitored |----+                  |
             |     +-------------------+                   ----+
             |
             |     +===================+
             +---->|     Fin           |
                   +===================+

 */

  private val log = Logger[this.type]

  sealed abstract class TargetState(val so: Int) {
    val node: LNode[TargetState,Unit] = LNode(this,())
  }


  object TargetState {
    case object Unknown extends TargetState(0) {
      override def toString = "Unknown"
    }

    case object Unmonitored extends TargetState(1) {
      override def toString = "Unmonitored"
    }

    case object Unmonitorable extends TargetState(8) {
      override def toString = "Unmonitorable"
    }

    case object Investigating extends TargetState(9) {
      override def toString = "Investigating"
    }

    case object Assigned extends TargetState(2) {
      override def toString = "Assigned"
    }

    case object Monitored extends TargetState(3) {
      override def toString = "Monitored"
    }

    case object DoubleAssigned extends TargetState(4) {
      override def toString = "DoubleAssigned"
    }

    case object Problematic extends TargetState(7) {
      override def toString = "Problematic"
    }

    case object DoubleMonitored extends TargetState(5) {
      override def toString = "DoubleMonitored"
    }

    case object Fin extends TargetState(6) {
      override def toString = "Fin"
    }

    implicit val stateOrder: Order[TargetState] = Order[Int].contramap(_.so)
  }


  sealed trait TargetTransition
  case object Discover extends TargetTransition
  case object Assign extends TargetTransition
  case object Confirm extends TargetTransition
  case object Migrate extends TargetTransition
  case object Unassign extends TargetTransition
  case object Exceptional extends TargetTransition
  case object Unmonitor extends TargetTransition
  case object Terminate extends TargetTransition


  type S = LNode[TargetState, Unit]
  type M = LEdge[TargetState, TargetTransition]
  type G = Graph[TargetState, Unit, TargetTransition]

  import TargetState._

  val nodes: Seq[S] = Seq(Unknown.node,
                          Unmonitored.node,
                          Assigned.node,
                          Monitored.node,
                          DoubleAssigned.node,
                          DoubleMonitored.node,
                          Problematic.node,
    			  Investigating.node,
    			  Fin.node)

                                // From          // To             // Msg
  val edges: Seq[M] = Seq(LEdge(Unknown,         Unmonitored,     Discover),
                          LEdge(Unmonitored,     Assigned,        Assign),
                          LEdge(Assigned,        Monitored,       Confirm),
                          LEdge(Assigned,        Unmonitored,     Unmonitor),
                          LEdge(Monitored,       DoubleAssigned,  Assign),
                          LEdge(DoubleAssigned,  DoubleMonitored, Confirm),
                          LEdge(DoubleMonitored, Assigned,        Unmonitor),
                          LEdge(Unknown,         Problematic,     Exceptional),
                          LEdge(Unmonitored,     Problematic,     Exceptional),
                          LEdge(Assigned,        Problematic,     Exceptional),
                          LEdge(Monitored,       Problematic,     Exceptional),
                          LEdge(DoubleAssigned,  Problematic,     Exceptional),
                          LEdge(DoubleMonitored, Problematic,     Exceptional),
                          LEdge(Fin,             Problematic,     Exceptional),
                          LEdge(Unknown,         Fin,             Terminate),
                          LEdge(Unmonitored,     Fin,             Terminate),
                          LEdge(Assigned,        Fin,             Terminate),
                          LEdge(Monitored,       Fin,             Terminate),
                          LEdge(DoubleAssigned,  Fin,             Terminate),
                          LEdge(DoubleMonitored, Fin,             Terminate),
                          LEdge(Problematic,     Investigating,   Exceptional),
                          LEdge(Investigating,   Unmonitored,     Discover),
                          LEdge(Investigating,   Fin,             Terminate),
                          LEdge(Monitored,       Unmonitored,     Unmonitor))

  val targetLifecycle: G = mkGraph(nodes, edges)

  sealed abstract class TargetMessage {
    def transition: TargetTransition
    def target: Target
    def time: Long
  }

  case class Discovery(target: Target, time: Long) extends TargetMessage {
    val transition = Discover
  }
  case class Assignment(target: Target, flask: FlaskID, time: Long) extends TargetMessage {
    val transition = Assign
  }
  case class Confirmation(target: Target, flask: FlaskID, time: Long) extends TargetMessage {
    val transition = Confirm
  }
  case class Migration(target: Target, flask: FlaskID, time: Long) extends TargetMessage {
    val transition = Migrate
  }
  case class Unassignment(target: Target, flask: FlaskID, time: Long) extends TargetMessage {
    val transition = Unassign
  }
  case class Unmonitoring(target: Target, flask: FlaskID, time: Long) extends TargetMessage {
    val transition = Unmonitor
  }
  case class Problem(target: Target, flask: FlaskID, msg: String, time: Long) extends TargetMessage {
    val transition = Exceptional
  }
  case class Investigate(target: Target, time: Long, retryCount: Int) extends TargetMessage {
    val transition = Exceptional
  }
  case class Terminated(target: Target, time: Long) extends TargetMessage {
    val transition = Terminate
  }

  def process(repo: Repository)(msg: TargetMessage, state: TargetState): Task[Unit] = {
    def findTrans(msg: TargetMessage, from: TargetState, v: Vector[(TargetTransition, TargetState)]): Option[RepoEvent.StateChange] = v match {
      case (t,s) +: vs if (t == msg.transition) => Some(RepoEvent.StateChange(from, s, msg))
      case _ +: vs => findTrans(msg, from, vs)
      case _ => None
    }

    targetLifecycle decomp state match {
      case Decomp(Some(Context(_, _, _, out)), _) =>
        findTrans(msg, state, out).fold(
          Task.now(log.error(s"found context but failed: msg=$msg, state=$state, out=$out, repostates=${repo.states}"))
            )(repo.processRepoEvent)

      case other => Task.now(log.error(
        s"transistion process failed: msg=$msg, state=$state, other=$other"))
    }
  }
}
