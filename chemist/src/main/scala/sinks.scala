package funnel
package chemist

import scalaz.Nondeterminism
import scalaz.concurrent.Task
import scalaz.stream.{Sink,sink}
import scalaz.stream.async.mutable.Queue
import journal.Logger

object sinks {
  import Sharding.Distribution
  import Chemist.Context
  import FlaskCommand._

  val log = Logger[sinks.type]

  def caching(to: StateCache): Sink[Task, Context[Plan]] =
    sink.lift { c =>
      for {
        _ <- to.plan(c.value)
        _ <- to.distribution(c.distribution)
      } yield ()
    }

  // really only used for testing purposes
  val logging: Sink[Task, Context[Plan]] =
    sink.lift(c => Task.delay(log.debug(s"recieved $c")))

  def unsafeNetworkIO(f: RemoteFlask, queue: Queue[PlatformEvent]): Sink[Task, Context[Plan]] = sink.lift {
    case Context(d, Distribute(work)) => {
      val tasks = work.fold(List.empty[Task[Unit]]){ (a,b,c) =>
        c :+ f.command(Monitor(a,b))
      }
      Nondeterminism[Task].gatherUnordered(tasks).map(_ => ())
    }

    case Context(d, p@Redistribute(stop, start)) =>
      Task.delay(log.info("recieved redistribute command (not implemented): $p"))

    case Context(d, Ignore) =>
      Task.delay(())
  }
}
