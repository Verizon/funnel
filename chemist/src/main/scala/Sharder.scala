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

import Sharding.Distribution
import journal.Logger
import scalaz.std.set._

trait Sharder {
  /**
   * provide the new distribution based on the result of calculating
   * how the new set should actually be distributed. main benefit here
   * is simply making the operations opaque (handling missing key cases)
   *
   * Returned distribution includes all previous assignments as well as assignments for nee targets.
   */
  def distribution(s: Set[Target])(d: Distribution): Distribution
}

trait PartialSharder {
  /**
    * Variant of Sharder that only knows how to distribute some subset of work.
    * E.g. FlaskStreamsSharder ensures that Flasks are responsible for mirroring their own metric streams.
    *
    * Returns new distribution including previous assignments and newly assigned work and set of targets that had
    * not been distributed.
    */
  def distribute(s: Set[Target])(d: Distribution): (Distribution, Set[Target])
}

object FlaskStreamsSharder extends PartialSharder {
  private[this] val log = Logger[FlaskStreamsSharder.type]

  private def calculate(s: Set[Target])(d: Distribution): Seq[(Flask,Target)] = {
    val rules: Map[String, Flask] = Sharding.shards(d).map(f => f.location.host -> f).toMap

    s.toSeq.flatMap {
      t => rules.get(t.uri.getHost).map(f => (f, t))
    }
  }

  //ensure that flask own streams are always assigned to flasks
  def distribute(s: Set[Target])(d: Distribution): (Distribution, Set[Target]) = {
    val work = calculate(s)(d)

    val dist = work.foldLeft(d) { (a,b) => a.updateAppend(b._1, Set(b._2)) }

    val otherTargets = s.diff(work.map(_._2).toSet)

    log.debug(s"[FlaskStreamsSharder] assigned ${s.size - otherTargets.size} out of ${s.size}.\n  work=$work")

    (dist, otherTargets)
  }
}

object RandomSharding extends Sharder {
  private[this] val log = Logger[RandomSharding.type]
  private[this] val rnd = new scala.util.Random

  // Randomly assigns each target in `s` to a Flask in the distribution `d`.
  private def calculate(s: Set[Target])(d: Distribution): Seq[(Flask,Target)] = {
    val flasks = Sharding.shards(d)
    val range = flasks.indices
    if (flasks.isEmpty) Nil
    else {
      s.toList.map { t =>
        flasks(rnd.nextInt(range.length)) -> t
      }
    }
  }

  /**
   * Assign the targets in `s` randomly to the distribution in `d`.
   * Returns a pair of:
   *   The assignment of targets to flasks
   *   the new distribution
   *
   * Throws away the existing assignments UNLESS `s` is empty,
   * in which case it leaves `d` unchanged.
   *
   * `s`: The targets to distribute
   * `d`: The existing distribution
   */
  def distribution(ss: Set[Target])(old: Distribution): Distribution = {
    if (ss.isEmpty) old
    else {
      log.debug(s"distribution: attempting to distribute targets '${ss.mkString(",")}'")

      val (d, s) = FlaskStreamsSharder.distribute(ss)(old)

      val work = calculate(s)(d)

      log.debug(s"distribution: work = $work")

      val dist = work.foldLeft(d) { (a,b) => a.updateAppend(b._1, Set(b._2)) }

      log.debug(s"work = $work, dist = $dist")

      dist
    }
  }
}

/**
 * Implements a "least-first" round-robin sharding. The flasks are ordered
 * by the amount of work they currently have assigned, with the least
 * amount of work is ordered first, and then we round-robin the nodes in the
 * hope that most sharding calls happen when instances come online in small
 * groups.
 *
 * Downside of this sharder is that it often leads to "heaping" where over all
 * flask shards, the work is "heaped" to one end of the distribution curve.
 */
object LFRRSharding extends Sharder {
  private[this] val log = Logger[LFRRSharding.type]

  private def calculate(s: Set[Target])(d: Distribution): Seq[(Flask,Target)] = {
    val servers: IndexedSeq[Flask] = Sharding.shards(d)
    val ss                    = servers.size
    val input: Set[Target]    = Sharding.deduplicate(s)(d)

    log.debug(s"calculating the target distribution: servers=$servers, input=$input")

    if(ss == 0) {
      log.warn("there are no flask servers currently registered to distribute work too.")
      Nil // needed for when there are no Flask's in-memory; causes SOE.
    } else {
      // interleave the input with the known flask servers ordered by the
      // flask that currently has the least amount of work assigned.
      input.toStream.zip(Stream.continually(servers).flatten).toList.map(t => (t._2, t._1))
    }
  }

  def distribution(ss: Set[Target])(old: Distribution): Distribution = {
    // this check is needed as otherwise the fold gets stuck in a gnarly
    // infinite loop, and this function never completes.
    if (ss.isEmpty) old
    else {
      log.debug(s"distribution: attempting to distribute targets '${ss.mkString(",")}'")

      val (d, s) = FlaskStreamsSharder.distribute(ss)(old)

      val work = calculate(s)(d)

      log.debug(s"distribution: work = $work")

      val dist = work.foldLeft(d) { (a,b) => a.updateAppend(b._1, Set(b._2)) }

      log.debug(s"work = $work, dist = $dist")

      dist
    }
  }
}
