package funnel
package chemist

import scalaz.stream.{Process,Process1,Sink,async,time,channel,wye,sink}
import scalaz.concurrent.{Task,Strategy}
import scala.concurrent.duration._
import Sharding.Distribution
import scalaz.{\/,\/-}
import PlatformEvent._
import java.net.URI

object Pipeline {

  type Flow[A] = Process[Task,Context[A]]

  case class Context[A](distribution: Distribution, value: A)

  // discovery
  def discover(dsc: Discovery): Flow[Target] = {
    time.awakeEvery(10.seconds)(Strategy.Executor(Chemist.serverPool), Chemist.schedulingPool).flatMap { _ =>
      val task: Task[Seq[Context[Target]]] = for {
        a <- dsc.listActiveFlasks
        b <- dsc.listTargets.map(_.map(_._2).flatten)
        c  = a.foldLeft(Distribution.empty){ (x,y) => x.insert(y, Set.empty[Target]) }
      } yield b.map(Context(c,_))

      Process.eval(task).flatMap(Process.emitAll)
    }
  }

  def contextualise[A](a: A): Context[A] =
    Context(Distribution.empty, a)

  // purpose of this function is to grab the existing work from the shards,
  // and update the distribution - our view of the world as it is right now
  def collect(flasks: Distribution): Task[Distribution] = ???

  object handle {
    def newTarget(target: Target)(d: Distribution): (Distribution, Distribution) = ???
    def newFlask(e: NewFlask)(d: Distribution): (Distribution, Distribution) = ???
    def terminatedTarget(e: TerminatedTarget)(d: Distribution): (Distribution, Distribution) = ???
    def unmonitored(e: Unmonitored)(d: Distribution): (Distribution, Distribution) = ???
  }

  // routing
  def partition(dsc: Discovery, shd: Sharder)(c: Context[PlatformEvent]): Context[Plan] =
    c match {
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

  val action: Sink[Task, Context[Plan]] = sink.lift {
    case Context(d, Distribute(work)) => Task.delay(()) // itterate the work and do I/O to send to flask
    case Context(d, Ignore)           => Task.delay(())
  }

  val caches: Sink[Task, Context[Plan]] =
    sink.lift { c =>
      for {
        _ <- MemoryStateCache.plan(c.value)
        _ <- MemoryStateCache.distribution(c.distribution)
      } yield ()
    }

  def discovery(d: Discovery, gather: Distribution => Task[Distribution]): Process[Task,Context[PlatformEvent]] =
    discover(d).evalMap { case Context(a,b) =>
      for(dist <- gather(a)) yield {
        val current: Vector[Target] = dist.values.toVector.flatten
        val event: PlatformEvent = if(current.exists(_ == b)) NoOp
                                   else NewTarget(b)
        Context(dist, event)
      }
    }

  /********* edge of the world *********/

  // needs error handling
  def program(
    lifecycle: Flow[PlatformEvent]
  )(dsc: Discovery,
    shd: Sharder,
    caches: Sink[Task, Context[Plan]],
    effects: Sink[Task, Context[Plan]]
  ): Task[Unit] =
    discovery(dsc, collect _)
      .wye(lifecycle)(wye.merge)
      .map(partition(dsc,shd))
      .observe(caches)
      .to(effects).run
}