package funnel
package chemist

import scalaz.stream.{Process,Process1,Sink,time,async,wye,sink}
import scalaz.stream.async.mutable.Signal
import scalaz.concurrent.{Task,Strategy}
import scala.concurrent.duration._
import Sharding.Distribution
import scalaz.{\/,\/-}
import PlatformEvent._
import java.net.URI

sealed trait Plan
case class Distribute(dist: Distribution) extends Plan
case object Ignore extends Plan

object Prototype {
  def main(args: Array[String]): Unit = {

    val s1 = Strategy.Executor(Chemist.serverPool)
    val signal: Signal[Boolean] = async.signalOf(true)

    case class Context[A](distribution: Distribution, value: A)

    type Flow[A] = Process[Task,Context[A]]

    // discovery
    def discover(dsc: Discovery): Flow[Target] = {
      time.awakeEvery(10.seconds)(s1, Chemist.schedulingPool).flatMap { _ =>
        val task: Task[Seq[Context[Target]]] = for {
          a <- dsc.listActiveFlasks
          b <- dsc.listTargets.map(_.map(_._2).flatten)
          c  = a.foldLeft(Distribution.empty){ (x,y) => x.insert(y.id, Set.empty[Target]) }
        } yield b.map(Context(c,_))

        Process.eval(task).flatMap(Process.emitAll)
      }
    }

    // lifecycle
    val p2: Flow[PlatformEvent] =
      time.awakeEvery(40.seconds)(s1, Chemist.schedulingPool
        ).evalMap(d => Task.delay {
          Context[PlatformEvent](Distribution.empty, NewTarget(Target(s"test-${d.toMillis}", new URI("http://google.com"))))
        })

    // purpose of this function is to grab the existing work from the shards,
    // and update the distribution - our view of the world as it is right now
    def collect(flasks: Distribution): Distribution = ???

    // merge
    def join(d: Discovery)(p2: Flow[PlatformEvent]): Flow[PlatformEvent] = {
      val p1: Process[Task,Context[PlatformEvent]] =
        discover(d).map { case Context(a,b) =>
          val dist = collect(a)
          val current: Vector[Target] = dist.values.toVector.flatten
          val event: PlatformEvent = if(current.exists(_ == b)) NoOp
                                     else NewTarget(b)
          Context(dist, event)
        }
      p1.wye(p2)(wye.merge)
    }

    object handle {
      def newTarget(target: Target)(d: Distribution): (Distribution, Distribution) = ???
      def newFlask(e: NewFlask)(d: Distribution): (Distribution, Distribution) = ???
      def terminatedTarget(e: TerminatedTarget)(d: Distribution): (Distribution, Distribution) = ???
      def unmonitored(e: Unmonitored)(d: Distribution): (Distribution, Distribution) = ???
    }

    // routing
    def partition(dsc: Discovery, shd: Sharder)(lifecycle: Flow[PlatformEvent]): Flow[Plan] = {
      join(dsc)(lifecycle).map {
        case Context(d,NewTarget(target)) =>
          val (all,work) = handle.newTarget(target)(d)
          Context(all, Distribute(work))

        case Context(d,NewFlask(flask)) =>
          sys.error("not implemented")

        case Context(d,TerminatedTarget(uri)) =>
          sys.error("not implemented")

        case Context(d,TerminatedFlask(flaskid)) =>
          sys.error("not implemented")

        case Context(d,Unmonitored(flaskid, uri)) =>
          sys.error("not implemented")

        case Context(d,NoOp) =>
          Context(d, Ignore)
      }
    }

    val action: Sink[Task, Plan] = sink.lift {
      case Distribute(work) => Task.delay(()) // itterate the work and do I/O to send to flask
      case Ignore           => Task.delay(())
    }
  }
}

