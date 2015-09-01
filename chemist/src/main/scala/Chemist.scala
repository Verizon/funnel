package funnel
package chemist

import knobs.{loadImmutable,Required,FileResource,ClassPathResource}
import java.io.File
import java.net.{ InetSocketAddress, Socket, URI, URL }
import journal.Logger
import oncue.svc.funnel.BuildInfo
import scalaz.{\/,-\/,\/-,Kleisli}
import scalaz.syntax.kleisli._
import scalaz.syntax.traverse._
import scalaz.syntax.id._
import scalaz.std.vector._
import scalaz.std.option._
import scalaz.concurrent.Task
import scalaz.stream.{Process,Process0, Sink}
import java.util.concurrent.{Executors, ExecutorService, ScheduledExecutorService, ThreadFactory}

trait Chemist[A <: Platform]{
  type ChemistK[U] = Kleisli[Task, A, U]

  private val log = Logger[this.type]

  //////////////////////// PUBLIC API ////////////////////////////

  /**
   * Of all known monitorable services, dispaly the current work assignments
   * of funnel -> flask.
   */
  def distribution: ChemistK[Map[FlaskID, Map[ClusterName, List[URI]]]] =
    config.flatMapK(_.repository.distribution.map(Sharding.snapshot))

  /**
   * manually ask chemist to assign the given urls to a flask in order
   * to be monitored. This is not recomended as a daily-use function; chemist
   * should be smart enough to figure out when things go on/offline automatically.
   */
  def distribute(targets: Set[Target]): ChemistK[Unit] =
    Task.now(()).liftKleisli

  /**
   * list all the shards currently known by chemist.
   */
  def shards: ChemistK[Set[Flask]] =
    for {
      cfg <- config
      a <- cfg.repository.distribution.map(Sharding.shards).liftKleisli
    } yield a.toList.flatMap(id => cfg.repository.flask(id)).toSet

  /**
   * display all known node information about a specific shard
   */
  def shard(id: FlaskID): ChemistK[Option[Flask]] =
    config.map(_.repository.flask(id))

  /**
   * Instruct flask to specifcally take a given shard out of service and
   * repartiion its given load to the rest of the system.
   */
  def exclude(shard: FlaskID): ChemistK[Unit] =
    for {
      cfg <- config
      _ <- cfg.repository.platformHandler(PlatformEvent.TerminatedFlask(shard)).liftKleisli
    } yield ()

  /**
   * Instruct flask to specifcally "launch" a given shard and
   * start sending new load to the "new" shard.
   *
   * NOTE: Assumes all added instances here are free of work already.
   */
  def include(id: FlaskID): ChemistK[Unit] =
    for {
      cfg <- config
      flask <- cfg.discovery.lookupFlask(id).liftKleisli
      _ <- cfg.repository.platformHandler(PlatformEvent.NewFlask(flask)).liftKleisli
    } yield ()

  /**
   * List out the last 100 lifecycle events that this chemist has seen.
   */
  def platformHistory: ChemistK[Seq[PlatformEvent]] =
    config.flatMapK(_.repository.historicalPlatformEvents.map(_.filterNot(_ == PlatformEvent.NoOp)))

  /**
    * List the unmonitorable targets.
    */
  def listUnmonitorableTargets: ChemistK[List[Target]] =
    config.flatMapK { cfg => cfg.discovery.listUnmonitorableTargets.map(_.toList.flatMap(_._2))
  }

  /**
   * List out the last 100 lifecycle events that this chemist has seen.
   */
  def repoHistory: ChemistK[Seq[RepoEvent]] =
    config.flatMapK(_.repository.historicalRepoEvents)

  /**
   * List out the all the known Funnels and the state they are in.
   */
  def states: ChemistK[Map[TargetLifecycle.TargetState, Map[URI, RepoEvent.StateChange]]] =
    config.flatMapK(_.repository.states)

  /**
   * List out the last 100 Errors that this chemist has seen.
   */
  def errors: ChemistK[Seq[Error]] =
    config.flatMapK(_.repository.errors)

  /**
   * Force chemist to re-read the world from AWS. Useful if for some reason
   * Chemist gets into a weird state at runtime.
   */
  def bootstrap: ChemistK[Unit] = for {
    cfg <- config

    // from the whole world, figure out which are flask instances
    f  <- cfg.discovery.listFlasks.liftKleisli
    _  = log.info(s"found ${f.length} flasks in the running instance list...")

    // ask those flasks for their current work and yield a `Distribution`
    d <- Housekeeping.gatherAssignedTargets(f)(cfg.http).liftKleisli
    _  = log.debug(s"read the existing state of assigned work from the remote instances: $d")

    // update the distribution accordingly
    d2 <- cfg.repository.mergeExistingDistribution(d).liftKleisli
    _  = log.debug(s"merged the currently assigned work. distribution=$d2")

    // update the distribution with new flask shards
    _ <- f.toVector.traverse_(flask => cfg.repository.platformHandler(PlatformEvent.NewFlask(flask))).liftKleisli
    _  = log.debug(s"increased the number of known flasks to ${f.size}")

    _ <- Housekeeping.gatherUnassignedTargets(cfg.discovery, cfg.repository).liftKleisli

    _ <- Task.now(log.info(">>>>>>>>>>>> boostrap complete <<<<<<<<<<<<")).liftKleisli

  } yield ()

  /**
   * Initilize the chemist serivce by trying to create the various AWS resources
   * that are required to operate. Once complete, execute the boostrap.
   */
  def init: ChemistK[Unit]

  //////////////////////// INTERNALS ////////////////////////////

  protected def platform: ChemistK[A] =
    Kleisli.ask[Task, A]

  protected val config: ChemistK[A#Config] =
    platform.map(_.config)

}

object Chemist {

  private def daemonThreads(name: String) = new ThreadFactory {
    def newThread(r: Runnable) = {
      val t = Executors.defaultThreadFactory.newThread(r)
      t.setDaemon(true)
      t.setName(name)
      t
    }
  }

  val version: String =
    s"Chemist ${BuildInfo.version} (${BuildInfo.gitRevision})"

  val defaultPool: ExecutorService =
    Executors.newFixedThreadPool(4, daemonThreads("chemist-thread"))

  val serverPool: ExecutorService =
    Executors.newCachedThreadPool(daemonThreads("chemist-server"))

  val schedulingPool: ScheduledExecutorService =
    Executors.newScheduledThreadPool(2, daemonThreads("chemist-scheduled-tasks"))

  def contact(uri: URI): Throwable \/ Unit =
    \/.fromTryCatchThrowable[Unit, Exception]{
      val s = new Socket
      // timeout in 300ms to keep the overhead reasonable
      try s.connect(new InetSocketAddress(uri.getHost, uri.getPort), 300)
      finally s.close // whatever the outcome, close the socket to prevent leaks.
    }
}

