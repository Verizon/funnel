package funnel
package chemist

import scalaz.{==>>,Order}
import scalaz.std.set._
import scalaz.std.string._
import scalaz.std.tuple._
import scalaz.std.vector._
import scalaz.syntax.monad._
import scalaz.syntax.traverse.{ToFunctorOps => _, _}
import scalaz.concurrent.Task
import scalaz.stream.{Process,Process1,Sink}
import funnel.ClusterName
import java.net.URI

import journal.Logger

object Sharding {

  type Distribution = FlaskID ==>> Set[Target]

  object Distribution {
    def empty: Distribution = ==>>()
  }

  private lazy val log = Logger[Sharding.type]

  /**
   * obtain a list of flasks ordered by flasks with the least
   * assigned work first.
   */
  def shards(d: Distribution): IndexedSeq[FlaskID] =
    sorted(d).map(_._1).toIndexedSeq

  /**
   * sort the current distribution by the size of the url
   * set currently assigned to the index flask. Resulting
   * snapshot is ordered by flasks with least assigned
   * work first.
   */
  def sorted(d: Distribution): Seq[(FlaskID, Set[Target])] =
    d.toList.sortBy(_._2.size)

  /**
   * dump out the current snapshot of how chemist believes work
   * has been assigned to flasks.
   */
  def snapshot(d: Distribution): Map[FlaskID, Map[ClusterName, List[URI]]] =
    d.toList.map { case (i,s) =>
      i -> s.groupBy(_.cluster).mapValues(_.toList.map(_.uri))
    }.toMap

  /**
   * obtain the entire set of what chemist views as the
   * distributed world of urls.
   */
  def targets(d: Distribution): Set[Target] =
    d.values match {
      case Nil   => Set.empty[Target]
      case other => other.reduceLeft(_ ++ _)
    }

  /**
   * Given a set of inputs, check against the current known set of urls
   * that we're not already monitoring the inputs (thus ensuring that
   * the cluster is not having duplicated monitoring items)
   */
  private[chemist] def deduplicate(next: Set[Target])(d: Distribution): Set[Target] = {
    // get the full list of targets we currently know about
    val existing = targets(d)

    // determine if any of the supplied urls are existing targets
    val delta = next.map(_.uri) &~ existing.map(_.uri)

    // having computed the targets that we actually care about,
    // rehydrae a `Set[Target]` from the given `Set[SafeURL]`
    delta.foldLeft(Set.empty[Target]){ (a,b) =>
      a ++ next.filter(_.uri == b)
    }
  }

  ///////////////////////// IO functions ///////////////////////////

  def distribute(repo: Repository, sharder: Sharder, remote: RemoteFlask, dist: Distribution)(ts: Set[Target]): Task[Unit] = {
    val (s,newdist) = sharder.distribution(ts)(dist)
    s.toVector.traverse_ { x =>
      val flask = repo.flask(x._1)
      flask.fold(Task.delay(log.error("asked to assign to an unknown flask: " + x._1))){ f =>
        repo.platformHandler(PlatformEvent.Assigned(x._1, x._2)) >>
        remote.command(FlaskCommand.Monitor(f,Seq(x._2)))
      }
    } <* repo.mergeDistribution(newdist)
  }

  def handleRepoCommand(repo: Repository, sharder: Sharder, remote: RemoteFlask)(c: RepoCommand): Task[Unit] = {
    Task.delay(log.info(s"handleRepoCommand: $c")) >>
    repo.distribution.flatMap { dist =>
      c match {
        case RepoCommand.Monitor(t) =>
          distribute(repo, sharder, remote, dist)(Set(t))
        case RepoCommand.Unmonitor(fl, t) =>
          remote.command(FlaskCommand.Unmonitor(fl, Seq(t)))
        case RepoCommand.Telemetry(fl) =>
          remote.command(FlaskCommand.Telemetry(fl))
        case RepoCommand.ReassignWork(fl) =>
          repo.assignedTargets(fl) flatMap distribute(repo, sharder, remote, dist)
      }
    }
  }
}

trait Sharder {
  import Sharding._
  /**
   * provide the new distribution based on the result of calculating
   * how the new set should actually be distributed. main benifit here
   * is simply making the operations opaque (handling missing key cases)
   *
   * Returns values of this function represent two things:
   * 1. `Seq[(Flask,Target)]` is a sequence of targets zipped with the flask it was assigned too.
   * 2. `Distribution` is that same sequence folded into a `Distribution` instance which can
   *     then be added to the existing state of the world.
   */
  def distribution(s: Set[Target])(d: Distribution): (Seq[(FlaskID,Target)], Distribution)
}

object RandomSharding extends Sharder {
  import Sharding._

  private lazy val log = Logger[RandomSharding.type]
  private val rnd = new scala.util.Random

  private def calculate(s: Set[Target])(d: Distribution): Seq[(FlaskID,Target)] = {
    val flasks = shards(d)
    val range = flasks.indices
    if(flasks.size == 0) Nil
    else {
      s.toList.map { t =>
        flasks(rnd.nextInt(range.length)) -> t
      }
    }
  }

  def distribution(s: Set[Target])(d: Distribution): (Seq[(FlaskID,Target)], Distribution) = {
    if(s.isEmpty) (Seq.empty,d)
    else {
      log.debug(s"distribution: attempting to distribute targets '${s.mkString(",")}'")
      val work = calculate(s)(d)

      log.debug(s"distribution: work = $work")

      val dist = work.foldLeft(Distribution.empty) { (a,b) => a.updateAppend(b._1, Set(b._2)) }

      log.debug(s"work = $work, dist = $dist")

      (work, dist)
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
  import Sharding._
  private lazy val log = Logger[LFRRSharding.type]

  private def calculate(s: Set[Target])(d: Distribution): Seq[(FlaskID,Target)] = {
    val servers: Seq[FlaskID] = shards(d)
    val ss                    = servers.size
    val input: Set[Target]    = deduplicate(s)(d)

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

  def distribution(s: Set[Target])(d: Distribution): (Seq[(FlaskID,Target)], Distribution) = {
    // this check is needed as otherwise the fold gets stuck in a gnarly
    // infinate loop, and this function never completes.
    if(s.isEmpty) (Seq.empty,d)
    else {
      log.debug(s"distribution: attempting to distribute targets '${s.mkString(",")}'")
      val work = calculate(s)(d)

      log.debug(s"distribution: work = $work")

      val dist = work.foldLeft(Distribution.empty) { (a,b) => a.updateAppend(b._1, Set(b._2)) }

      log.debug(s"work = $work, dist = $dist")

      (work, dist)
    }
  }
}
