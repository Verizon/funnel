package funnel
package chemist

import knobs.{loadImmutable,Required,FileResource,ClassPathResource}
import java.io.File
import java.net.URI
import journal.Logger
import oncue.svc.funnel.BuildInfo
import scalaz.{\/,-\/,\/-,Kleisli}
import scalaz.syntax.kleisli._
import scalaz.concurrent.Task
import scalaz.stream.{Process,Process0, Sink}
import java.util.concurrent.{Executors, ExecutorService, ScheduledExecutorService, ThreadFactory}
import messages.Error

trait Chemist[A <: Platform]{
  type ChemistK[U] = Kleisli[Task, A, U]

  private val log = Logger[this.type]

  //////////////////////// PUBLIC API ////////////////////////////

  /**
   * Of all known monitorable services, dispaly the current work assignments
   * of funnel -> flask.
   */
  def distribution: ChemistK[Map[Flask, Map[String, List[URI]]]] =
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
    } yield a.toSet
  /**
   * display all known node information about a specific shard
   */
  def shard(id: FlaskID): ChemistK[Option[Flask]] =
    shards.map(_.find(_.id.value.toLowerCase == id.value.trim.toLowerCase))


  /**
   * Instruct flask to specifcally take a given shard out of service and
   * repartiion its given load to the rest of the system.
   */
  def exclude(shard: FlaskID): ChemistK[Unit] = {
      for {
        cfg <- config
        _ <- Sharding.platformHandler(cfg.repository)(PlatformEvent.TerminatedFlask(shard)).liftKleisli
      } yield ()
    }

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
      _ <- Sharding.platformHandler(cfg.repository)(PlatformEvent.NewFlask(flask)).liftKleisli
    } yield ()

  /**
   * List out the last 100 lifecycle events that this chemist has seen.
   */
  def history: ChemistK[Seq[RepoEvent]] =
    config.flatMapK(_.repository.historicalEvents)

  /**
   * List out the last 100 Errors that this chemist has seen.
   */
  def errors: ChemistK[Seq[Error]] =
    config.flatMapK(_.repository.errors)

  /**
   * Force chemist to re-read the world. Useful if for some reason
   * Chemist gets into a weird state at runtime.
   */
  def bootstrap: ChemistK[Unit]

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

}

