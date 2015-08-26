package funnel
package chemist

import java.net.URI
import scalaz.\/
import scalaz.concurrent.{Task,Strategy}
import scalaz.stream.{Process, time}
import scalaz.std.string._
import scalaz.std.set._
import scalaz.syntax.apply._
import scalaz.syntax.kleisli._
import scalaz.syntax.traverse._
import scalaz.syntax.id._
import scalaz.std.vector._
import scalaz.std.option._
import journal.Logger
import TargetLifecycle.{ Investigate, TargetMessage, TargetState }
import RepoEvent.StateChange

object Housekeeping {
  import Chemist.contact
  import Sharding._
  import concurrent.duration._

  private lazy val log = Logger[Housekeeping.type]
  private lazy val defaultPool = Strategy.Executor(Chemist.defaultPool)

  /**
   * This is a collection of the tasks that are needed to run on a periodic basis.
   * Specifically this involves the following:
   * 1. ensuring that we pickup any target instances that we were not already moniotring
   * 2. ensuring that targets that are stuck in the assigned state for more than a given
   *    period are reaped, and re-assigned to another flask (NOT IMPLEMENTED.)
   */
  def periodic(delay: Duration)(maxRetries: Int)(d: Discovery, r: Repository): Process[Task,Unit] = {
    time.awakeEvery(delay)(defaultPool, Chemist.schedulingPool).evalMap(_ =>
      for {
        _  <- gatherUnassignedTargets(d, r) <*
              handleInvestigating(maxRetries)(r)
      } yield ()
    )
  }

  /**
   * Gather up all the targets that are for reasons unknown sitting in the unassigned
   * target state. We'll read the list of the whole world, and delta that against the known
   * distribution, and assign any outliers. This is a fall back mechinism.
   */
  def gatherUnassignedTargets(discovery: Discovery, repository: Repository): Task[Unit] =
    for {
      // read the state of the existing world
      d <- repository.distribution

      // read the list of all deployed machines
      l <- discovery.listTargets
      _  = log.info(s"found a total of ${l.length} deployed, accessable instances...")

      // // figure out given the existing distribution, and the difference between what's been discovered
      // // STU: the fact that I'm throwing ID away here is suspect
      unmonitored = l.foldLeft(Set.empty[Target])(_ ++ _._2) -- d.values.foldLeft(Set.empty[Target])(_ ++ _)
      _  = log.info(s"located instances: monitorable=${l.size}, unmonitored=${unmonitored.size}")

      // // action the new targets by putting them in the monitoring lifecycle
      targets = unmonitored.map(t => PlatformEvent.NewTarget(t))
      _ <- targets.toVector.traverse_(repository.platformHandler)
      _  = log.info(s"added ${targets.size} targets to the repository...")
    } yield ()

  /**
   * Given a collection of flask instances, find out what exactly they are already
   * mirroring and absorb that into the view of the world.
   *
   * This function should only really be used startup of chemist.
   */
  def gatherAssignedTargets(flasks: Seq[Flask])(http: dispatch.Http): Task[Distribution] = {
    val d = (for {
       a <- Task.gatherUnordered(flasks.map(
         f => requestAssignedTargets(f.location)(http).map(f -> _).flatMap { t =>
           Task.delay {
             log.debug(s"Read targets $t from flask $f")
             t
           }
         }
       ))
    } yield a.foldLeft(Distribution.empty){ (a,b) =>
      a.updateAppend(b._1.id, b._2)
    }).map { dis =>
      log.debug(s"Gathered distribution $dis")
      dis
    }
    d or Task.now(Distribution.empty)
  }

  import funnel.http.{Cluster,JSON => HJSON}

  /**
   * Call out to the specific location and grab the list of things the flask
   * is already mirroring.
   */
  private def requestAssignedTargets(location: Location)(http: dispatch.Http): Task[Set[Target]] = {
    import argonaut._, Argonaut._, JSON._, HJSON._
    import dispatch._, Defaults._

    val a = location.uriFromTemplate(LocationTemplate(s"http://@host:@port/mirror/sources"))
    val req = Task.delay(url(a.toString)) <* Task.delay(log.debug(s"requesting assigned targets from $a"))
    req flatMap { b =>
      http(b OK as.String).map { c =>
        Parse.decodeOption[List[Cluster]](c
        ).toList.flatMap(identity
        ).map { cluster =>
          log.debug(s"Received cluster $cluster from $a")
          cluster
        }.foldLeft(Set.empty[Target]){ (a,b) =>
          b.urls.map(s => Target(b.label, new URI(s), false)).toSet
        }
      }
    }
  }

  private def handleInvestigating(maxRetries: Int)(r: Repository): Task[Unit] = {
    def go(sc: StateChange, r: Repository): Task[Unit] = {
      val i = sc.msg.asInstanceOf[Investigate]

      if (i.retryCount < maxRetries) {			// TODO: make max retries oonfigurable?
        val now = System.currentTimeMillis
        val later = i.time + math.pow(2.0, i.retryCount.toDouble).toInt.hours.toMillis
        if (now >= later) {				// Not done retrying; time to retry
          Task.fromDisjunction(contact(i.target.uri)).onFinish { ot => ot match {
            case Some(t) => Task.delay {		// Retry failed with Throwable t
              log.debug(s"Failed retrying target ${i.target} for the ${i.retryCount}th time, with Throwable $t")
            } <* r.updateState(
              i.target.uri,
              TargetState.Investigating,
              sc.copy(msg = Investigate(i.target, now, i.retryCount + 1))
            )
            case None    => Task.delay {		// Retry succeeded;
              log.debug(s"Succeeded retrying target ${i.target} for the ${i.retryCount}th time")
            } <* r.platformHandler(PlatformEvent.TerminatedTarget(i.target.uri)) <*
                 r.platformHandler(PlatformEvent.NewTarget(i.target))
          }}
        } else {					// Not done retrying but not time to retry yet
          Task.delay {
            log.debug(s"Target ${i.target} is being investigated, but it's not time to retry it right now")
          }
        }
      } else {						// Too many retries; ultimate failure
        Task.delay {
          log.debug(s"Target ${i.target} was retried too many times and is being terminated")
        } <* r.platformHandler(PlatformEvent.TerminatedTarget(i.target.uri))
      }
    }

    for {
      sm   <- r.states
      scs   = sm(TargetState.Investigating).values.toSeq
      _    <- Task.gatherUnordered(scs.map(go(_, r)))
    } yield ()
  }
}
