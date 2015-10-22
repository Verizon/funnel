package funnel
package chemist

import Sharding.Distribution
import scalaz.concurrent.Task
import scalaz.stream.{Sink,sink}
import journal.Logger

object sinks {
  import Chemist.Context

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

  val unsafeNetworkIO: Sink[Task, Context[Plan]] = sink.lift {
    case Context(d, Distribute(work)) => Task.delay(()) // itterate the work and do I/O to send to flask
    case Context(d, Ignore)           => Task.delay(())
  }
}
