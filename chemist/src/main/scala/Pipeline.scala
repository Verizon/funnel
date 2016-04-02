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
import scala.concurrent.duration._
import scalaz.concurrent.{Strategy, Task}
import scalaz.stream.async.mutable.Queue
import scalaz.stream.{Process, Sink, time, wye}

object Pipeline {
  import Chemist.{Context,Flow}
  import Sharding.Distribution
  import PlatformEvent._

  private val log = Logger[Pipeline.type]

  /**
    * Discover all the known Targets in a Context.
    */
  def targets(dsc: Discovery)(gather: Distribution => Task[Distribution]): Task[Context[PlatformEvent]] = for {
    inv <- dsc.inventory
    dist <- gather(Distribution.empty(inv.activeFlasks))
    b = inv.targets.flatMap(_._2)
  } yield Context[PlatformEvent](dist, AllTargets(b))

  /**
   * periodically wake up and call the platform discovery system. doing this
   * ensures that we capture any outliers, despite having the more event-based
   * platform lifecycle stream (which could periodically fail).
   */
  def discover(dsc: Discovery, interval: Duration)(gather: Distribution => Task[Distribution]): Flow[PlatformEvent] = {
    (Process.emit(Duration.Zero) ++ time.awakeEvery(interval)(
      Strategy.Executor(Chemist.serverPool), Chemist.schedulingPool
    )).evalMap[Task, Context[PlatformEvent]](_ => targets(dsc)(gather))
  }

  /**
   * basically just lift a given A into a Context A... perhaps this would be
   * better on the Context companion object?
   */
  def contextualise[A](a: A): Context[A] =
    Context(Distribution.empty, a)

  /**
   * grab the existing work from the shards, and update the distribution;
   * our view of the world as it is right now (stale and immediately non-authoritative)
   */
  def collect(http: dispatch.Http)(d: Distribution): Task[Distribution] =
    Flask.gatherAssignedTargets(Sharding.shards(d))(http)


  /**
    * Each of distribution update operations computes set of actions to perform (start or stop monitoring)
    * represented by Redistribute plan as well as "new" distribution after operations had been performed
    */
  object handle {
    /**
     * distribution is the specific work that needs to take place, represented as a distribution
     */
    def newTarget(target: Target, sharder: Sharder)(d: Distribution): (Distribution, Redistribute) = {
      val proposed = sharder.distribution(Set(target))(d)._2 // drop the seq, as its not needed

      //there is nothing to stop and we only need to issue command for new target
      (proposed, Redistribute(Distribution.empty, proposed.map(_.intersect(Set(target)))))
    }


    /**
      * computes distribution given full state of the world.
      * We do not want to reduce it to distributing each of new targets independently as
      * it gives us more flexibility if we know full set of changes to be made
      */
    def rediscovery(allTargets: Set[Target], old: Distribution)(shd: Sharder): (Distribution, Redistribute) = {
        val current: Set[Target] = Sharding.targets(old)
        val newTargets = allTargets.diff(current)
        val missingTargets = current.diff(allTargets)

        val proposed = shd.distribution(newTargets)(old)._2.map(_.diff(missingTargets))

        //redistribute command assumes we are not sending "redundant" requests to monitor something being monitored
        val proposedDelta = proposed.map(_.intersect(newTargets))
        val oldDelta = old.map(_.intersect(missingTargets))

        (proposed, Redistribute(oldDelta, proposedDelta))
      }

    /**
     * in the event more capacity becomes available, rebalance the cluster to take
     * best advantage of that new capacity using the specified sharder to
     * redistribute the work. This function is solely responsible for orchestrating
     * the inputs/outputs of the sharder, and the actual implementation logic of
     * what to shard where is entirely encapsulated in the `Sharder`.
     */
    def newFlask(flask: Flask, shd: Sharder)(old: Distribution): (Distribution, Redistribute) = {
      val flasks: IndexedSeq[Flask] = Sharding.shards(old)
      val targets: Set[Target] = Sharding.targets(old)
      val empty: Distribution = flasks.foldLeft(Distribution.empty)(
        (a,b) => a.insert(b, Set.empty)).insert(flask, Set.empty)

      val proposed: Distribution = shd.distribution(targets)(empty)._2

      val r1 = proposed.fold(Redistribute.empty){ (f, t, r) =>
        if(f.id == flask.id) r.update(f, stopping = Set.empty, starting = t)
        else {
          // targets previously assigned to this flask
          val previous = old.lookup(f).getOrElse(Set.empty[Target])
          // of those targets, see what work is already assigned to
          // the very same shard, and ignore it as its already good
          // where it is. any work that didnt match (i.e. wasn't on
          // this shard in the new state should be stopped for this
          // particular shard).
          val (ignore,_) = t.partition(t => previous.contains(t))
          // produce the redistribution for this flask
          r.update(f, previous -- ignore, t -- ignore)
        }
      }
      (proposed, r1)
    }
  }

  /**
   * a simple transducer that converts `PlatformEvent` into a `Plan` so that
   * the stream can be fed to whatever sink has been wired to this process.
   * this function should only ever be indicating what the intended actions
   * are, not actually doing any effectful I/O itself.
   */
  def transform(dsc: Discovery, shd: Sharder, gather: Distribution => Task[Distribution])
               (c: Context[PlatformEvent]): Task[Context[Plan]] = {
    c match {
      case Context(d, NewTarget(target)) => Task.delay {
        log.info(s"[transform] new target=$target allocated=${Sharding.targets(d).size}")
        val (proposed, work) = handle.newTarget(target, shd)(d)
        Context(proposed, work)
      }

      case Context(d, NewFlask(f)) => Task.delay {
        log.info(s"[transform] new flask=$f allocated=${Sharding.targets(d).size}")
        val (proposed, work) = handle.newFlask(f, shd)(d)
        Context(proposed, work)
      }

      case Context(d, TerminatedTarget(uri)) =>
        log.info(s"[transform] terminated target=$uri allocated=${Sharding.targets(d).size}")
        Task.delay(Context(d, Ignore))

      case Context(d, AllTargets(all)) => Task.delay {
        log.info(s"[transform] allTargets total=${all.size} allocated=${Sharding.targets(d).size} new=${all.toSet.diff(Sharding.targets(d)).size} diff=${all.toSet.diff(Sharding.targets(d))}")
        val (proposed, work) = handle.rediscovery(all.toSet, d)(shd)
        Context(proposed, work)
      }

      //if flask is terminated we probably do not have accurate state of what it was monitoring
      // => perform full discovery and redistribute work
      case Context(d, TerminatedFlask(flaskId)) =>
        log.info(s"[transform] terminated flask=$flaskId allocated=${Sharding.targets(d).size}")
        targets(dsc)(gather).flatMap {
          ctx =>
            //we just completed rediscovery and terminating flask should not be there but to be safe ...
            val ctx2: Context[PlatformEvent] =
              ctx.copy(distribution = ctx.distribution.filterWithKey((k, v) => k.id != flaskId))

            transform(dsc, shd, gather)(ctx2)
        }

      case Context(d, NoOp) =>
        log.info(s"[transform] NoOp allocated=${Sharding.targets(d).size}")
        Task.delay(Context(d, Ignore))
    }
  }

  /**
   * create the discovery stream by calling the discovery system and also
   * gathering a list of all the known work assigned to the flask as of
   * right now. difference the discovered work with the known work and then
   * produce `NewTarget` events for any remain, as they are not currently
   * being monitored.
   */
  def discovery(
    interval: Duration
  )(dsc: Discovery,
    shd: Sharder,
    gather: Distribution => Task[Distribution]
  ): Process[Task,Context[PlatformEvent]] =
    discover(dsc, interval)(gather)

  /********* edge of the world *********/

  /**
   * create a process that merges the discovery and lifecycle streams into a single
   * process, and then executes the mapping function to figure out what actions
   * should be executed (without actually executing them).
   */
  def process(
    lifecycle: Flow[PlatformEvent],
    pollInterval: Duration
  )(dsc: Discovery,
    shd: Sharder,
    http: dispatch.Http
  ): Process[Task, Context[Plan]] = {
    val S = Strategy.Executor(Chemist.defaultPool)
    //TODO: Add AllTargets event and let discovery emit it, then transform will apply to both
    discovery(pollInterval)(dsc, shd, collect(http)(_))
      .wye(lifecycle)(wye.merge)(S)
        .evalMap(transform(dsc,shd, collect(http)(_)))
  }

  // needs error handling
  def task(
    lifecycle: Flow[PlatformEvent],
    pollInterval: Duration
  )(dsc: Discovery,
    que: Queue[PlatformEvent],
    shd: Sharder,
    http: dispatch.Http,
    state: StateCache,
    effects: Sink[Task, Context[Plan]]
  ): Task[Unit] = {
    val ec: Sink[Task, Context[PlatformEvent]] =
      sinks.caching[PlatformEvent](state)

    val pc: Sink[Task, Context[Plan]] =
      sinks.caching[Plan](state)

    val lp: Flow[PlatformEvent] = que.dequeue.map(contextualise)
      .wye(lifecycle)(wye.merge)(Chemist.defaultExecutor)
      .observe(ec)

    process(lp, pollInterval)(dsc,shd,http)
      .observe(pc)
      .to(effects).run
  }
}
