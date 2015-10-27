package funnel
package chemist

import journal.Logger
import scalaz.Nondeterminism
import scalaz.concurrent.Task
import scalaz.stream.{Sink,sink}
import scalaz.stream.async.mutable.Queue

object sinks {
  import Sharding.Distribution
  import Chemist.Context
  import FlaskCommand._
  import scalaz.syntax.apply._

  private[this] val log = Logger[sinks.type]

  def caching(to: StateCache): Sink[Task, Context[Plan]] =
    sink.lift { c =>
      for {
        _ <- to.plan(c.value)
        _ <- to.distribution(c.distribution)
      } yield ()
    }

  /**
   * really only used for testing purposes; doesnt serve any
   * useful function other than for debugging.
   */
  val logging: Sink[Task, Context[Plan]] =
    sink.lift(c => Task.delay(log.debug(s"recieved $c")))

  /**
   * this should really only be used in proudction or integration
   * testing as it will actually reach out on the wire and do the
   * network I/O.
   */
  def unsafeNetworkIO(f: RemoteFlask, q: Queue[PlatformEvent]): Sink[Task, Context[Plan]] = sink.lift {
    case Context(d, Distribute(work)) => {
      val tasks = work.fold(List.empty[Task[Unit]]
        ){ (a,b,c) => c :+ f.command(Monitor(a,b)) }

      Task.delay(log.info("distributing work...")) <*
      Nondeterminism[Task].gatherUnordered(tasks)
    }

    case Context(d, p@Redistribute(stop, start)) => {
      val stopping = stop.fold(List.empty[Task[Unit]]
        ){ (a,b,c) => c :+ f.command(Unmonitor(a,b)) }

      val starting = start.fold(List.empty[Task[Unit]]
        ){ (a,b,c) => c :+ f.command(Monitor(a,b)) }

      Task.delay(log.info("recieved redistribute command: $p")) <*
      Nondeterminism[Task].gatherUnordered(stopping) <*
      Nondeterminism[Task].gatherUnordered(starting)
    }

    case Context(d, Produce(task)) =>
      Task.delay(log.debug("pushing synthetic platform events onto our own queue")) <*
      task.flatMap(q.enqueueAll(_))

    case Context(d, _) =>
      Task.delay(())
  }
}
