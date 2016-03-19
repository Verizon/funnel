//: ----------------------------------------------------------------------------
//: Copyright (C) 2015 Verizon.  All Rights Reserved.
//:
//:   Licensed under the Apache License, Version 2.0 (the "License");
//:   you may not use this file except in compliance with the License.
//:   You may obtain a copy of the License at
//:
//:       http://www.apache.org/licenses/LICENSE-2.0
//:
//:   Unless required by applicable law or agreed to in writing, software
//:   distributed under the License is distributed on an "AS IS" BASIS,
//:   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//:   See the License for the specific language governing permissions and
//:   limitations under the License.
//:
//: ----------------------------------------------------------------------------
package funnel
package chemist

import journal.Logger
import scalaz.Nondeterminism
import scalaz.concurrent.Task
import scalaz.stream.{Sink,sink}
import scalaz.stream.async.mutable.Queue

/**
 * module containing the process sinks used by chemist.
 * these sinks for the end of the world for the streams
 * and are where all the system effects should be placed.
 */
object sinks {
  import Chemist.Context
  import FlaskCommand._
  import scalaz.syntax.apply._

  private[this] val log = Logger[sinks.type]

  /**
   * create a caching sink from a `Cacheable[A]`. this is intended as
   * a simple convenience in creating sinks. Probably overkill, but
   * you know, whatever.
   */
  def caching[A : Cacheable](to: StateCache): Sink[Task, Context[A]] =
    sink.lift[Task, Context[A]](c => implicitly[Cacheable[A]].cache(c,to))

  /**
   * really only used for testing purposes; doesn't serve any
   * useful function other than for debugging.
   */
  val logging: Sink[Task, Context[Plan]] =
    sink.lift[Task, Context[Plan]](c => Task.delay(log.debug(s"received $c")))

  /**
   * this should really only be used in production or integration
   * testing as it will actually reach out on the wire and do the
   * network I/O.
   */
  def unsafeNetworkIO(f: RemoteFlask, q: Queue[PlatformEvent]): Sink[Task, Context[Plan]] = sink.lift[Task, Context[Plan]] {
    case Context(d, Distribute(work)) =>
      val tasks = work.fold(List.empty[Task[Unit]]) {
        (a,b,c) => c :+ f.command(Monitor(a,b)).handle {
          case t =>
            //swallow error if we fail to talk to individual flask (could be still coming up)
            //we will retry on the next iteration
            log.warn(s"Failed to distribute targets to flask. flask=${a.id} error=$t targets=$b")
        }
      }

      Task.delay(log.debug(s"distributing targets to flasks. work = $work")) <*
      Nondeterminism[Task].gatherUnordered(tasks)

    case Context(d, p@Redistribute(stop, start)) =>
      //this can fail but we can not swallow individual errors to prevent double monitoring
      val stopping = stop.fold(List.empty[Task[Unit]]) {
        (a,b,c) => c :+ f.command(Unmonitor(a,b))
      }

      //we can handle errors per flask here because we will only run this AFTER we successfully
      // executed "unmonitor" commands. Hence in worst case some targets will not be monitored
      // until next iteration
      val starting = start.fold(List.empty[Task[Unit]]) {
        (a,b,c) => c :+ f.command(Monitor(a,b)).handle {
          case t =>
            //swallow error if we fail to talk to individual flask (could be still coming up)
            //we will retry on the next iteration
            log.warn(s"Failed to redistribute targets to flask. flask=${a.id} error=$t targets=$b")
        }
      }

      (Task.delay(log.info(s"received redistribute command: $p")) <*
       Nondeterminism[Task].gatherUnordered(stopping) <*
       Nondeterminism[Task].gatherUnordered(starting)).handle {
        //this can fail as we did not handle errors for "unmonitor" commands
        case t =>
          //swallow error, will retry on next iteration
          // Note: if we got here some of "unmonitor" commands may be completed,
          //  depending on in what order gatherUnordered will execute them
          //  But none of "monitor" command were run.
          log.warn(s"Failed to redistribute targets. error=$t stop=$stop start=$start")
      }

    case Context(d, Produce(task)) =>
      Task.delay(log.debug("pushing synthetic platform events onto our own queue")) <*
      task.flatMap(q.enqueueAll)

    case Context(d, a) =>
      Task.delay(log.debug(s"nothing to see here but $a"))
  }
}
