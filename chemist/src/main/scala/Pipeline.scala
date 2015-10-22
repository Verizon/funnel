package funnel
package chemist

import java.net.URI
import journal.Logger
import scalaz.{\/,\/-}
import scala.concurrent.duration._
import scalaz.concurrent.{Task,Strategy}
import scalaz.stream.{Process,Process1,Sink,time,channel,wye,sink}

object Pipeline {
  import Chemist.{Context,Flow}
  import Sharding.Distribution
  import PlatformEvent._

  private[this] val log = Logger[Pipeline.type]

  // discovery
  def discover(dsc: Discovery, interval: Duration): Flow[Target] = {
    time.awakeEvery(interval)(Strategy.Executor(Chemist.serverPool), Chemist.schedulingPool).flatMap { _ =>
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
    /**
     * distribution is the specific work that needs to take place, represented as a distribution
     */
    def newTarget(target: Target, sharder: Sharder)(d: Distribution): Distribution =
      sharder.distribution(Set(target))(d)._2 // drop the seq, as its not needed

    /**
     * TODO: we need to add some logic to rebalance the cluster somehow,
     * as otherwise getting a new flask online will not aid in balencing the
     * overall workload of the cluster.
     */
    def newFlask(flask: Flask)(d: Distribution): Distribution = d

    def terminatedTarget(e: TerminatedTarget)(d: Distribution): Distribution = ???

    def unmonitored(e: Unmonitored)(d: Distribution): Distribution = ???
  }

  // routing
  def partition(dsc: Discovery, shd: Sharder)(c: Context[PlatformEvent]): Context[Plan] =
    c match {
      case Context(d,NewTarget(target)) =>
        val work = handle.newTarget(target, shd)(d)
        Context(d, Distribute(work))

      case Context(d,NewFlask(f)) =>
        Context(d, Redistribute(Distribution.empty, Distribution.empty))

      case Context(d,TerminatedTarget(uri)) =>
        Context(d, Ignore)

      // TIM: this is an interesting case. when we recieve a terminated flask
      // message... we don't know what the work that was previous assigned
      // to that flask, because that flask is now dead.
      // consider zipping another queue into the lifecycle stream, and having
      // the plan effect be emmitted a "discover now" message onto the stream,
      // forcing all the unmonitored targets to get monitored right away.
      case Context(d,TerminatedFlask(flask)) =>
        log.warn(s"encountered a terminated flask. oh shit, we didnt implement this yet!")
        Context(d, Ignore)

      case Context(d,s@Unmonitored(flaskid, uri)) =>
        log.warn(s"encountered an unexpected platform event state $s")
        Context(d, Ignore)

      case Context(d,NoOp) =>
        Context(d, Ignore)
    }

  def discovery(interval: Duration)(d: Discovery, gather: Distribution => Task[Distribution]): Process[Task,Context[PlatformEvent]] =
    discover(d, interval).evalMap { case Context(a,b) =>
      for(dist <- gather(a)) yield {
        val current: Vector[Target] = dist.values.toVector.flatten
        val event: PlatformEvent =
          if(current.exists(_ == b)) NoOp
          else NewTarget(b)
        Context(dist, event)
      }
    }

  /********* edge of the world *********/

  def process(
    lifecycle: Flow[PlatformEvent],
    pollInterval: Duration
  )(dsc: Discovery,
    shd: Sharder
  ): Process[Task, Context[Plan]] =
    discovery(pollInterval)(dsc, collect _)
      .wye(lifecycle)(wye.merge)
      .map(partition(dsc,shd))

  // needs error handling
  def task(
    lifecycle: Flow[PlatformEvent],
    pollInterval: Duration
  )(dsc: Discovery,
    shd: Sharder,
    caches: Sink[Task, Context[Plan]],
    effects: Sink[Task, Context[Plan]]
  ): Task[Unit] =
    process(lifecycle, pollInterval)(dsc,shd)
      .observe(caches)
      .to(effects).run
}