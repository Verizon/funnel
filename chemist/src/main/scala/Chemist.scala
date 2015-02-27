package funnel
package chemist

import knobs.{loadImmutable,Required,FileResource,ClassPathResource}
import java.io.File
import journal.Logger
import oncue.svc.funnel.BuildInfo
import scalaz.{\/,-\/,\/-,Kleisli}
import scalaz.syntax.kleisli._
import scalaz.concurrent.Task
import java.util.concurrent.{Executors, ExecutorService, ScheduledExecutorService, ThreadFactory}
import oncue.svc.funnel.BuildInfo

object Chemist {
  import Sharding.Target

  private val log = Logger[Chemist.type]

  //////////////////////// PUBLIC API ////////////////////////////

  val version: String =
    s"Chemist ${BuildInfo.version} (${BuildInfo.gitRevision})"

  /**
   * Of all known monitorable services, dispaly the current work assignments
   * of funnel -> flask.
   */
  def distribution: Chemist[Map[InstanceID, Map[String, List[SafeURL]]]] =
    config.flatMapK(_.repository.distribution.map(Sharding.snapshot))

  /**
   * manually ask chemist to assign the given urls to a flask in order
   * to be monitored. This is not recomended as a daily-use function; chemist
   * should be smart enough to figure out when things go on/offline automatically.
   */
  def distribute(targets: Set[Target]): Chemist[Unit] =
    Task.now(()).liftKleisli

  /**
   * list all the shards currently known by chemist.
   */
  def shards: Chemist[List[Instance]] =
    for {
      cfg <- config
      a <- cfg.repository.distribution.map(Sharding.shards).liftKleisli
      b <- Task.gatherUnordered(a.map(cfg.repository.instance).toSeq).liftKleisli
    } yield b

  /**
   * display all known node information about a specific shard
   */
  def shard(id: InstanceID): Chemist[Option[Instance]] =
    shards.map(_.find(_.id.toLowerCase == id.trim.toLowerCase))

  /**
   * Instruct flask to specifcally take a given shard out of service and
   * repartiion its given load to the rest of the system.
   */
  def exclude(shard: InstanceID): Chemist[Unit] =
    alterShard(shard, Terminate)

  /**
   * Instruct flask to specifcally "launch" a given shard and
   * start sending new load to the "new" shard.
   *
   * NOTE: Assumes all added instances here are free of work already.
   */
  def include(shard: InstanceID): Chemist[Unit] =
    alterShard(shard, Launch)

  /**
   * List out the last 100 lifecycle events that this chemist has seen.
   */
  def history: Chemist[Seq[AutoScalingEvent]] =
    config.flatMapK(_.repository.historicalEvents)

  /**
   * Force chemist to re-read the world from AWS. Useful if for some reason
   * Chemist gets into a weird state at runtime.
   */
  def bootstrap: Chemist[Unit] = for {
    cfg <- config
    // read the list of all deployed machines
    l <- Deployed.list(cfg.asg, cfg.ec2).liftKleisli
    _  = log.info(s"found a total of ${l.length} deployed, accessable instances...")

    // filter out all the instances that are in private networks
    // TODO: support VPCs by dynamically determining if chemist is in a vpc itself
    z  = l.filterNot(_.location.isPrivateNetwork)
          .filterNot(Deployed.isFlask)
    _  = log.info(s"located ${z.length} instances that appear to be monitorable")

    // convert the instance list into reachable targets
    t  = z.flatMap(Target.fromInstance(cfg.resources)).toSet
    _  = log.debug(s"targets are: $t")

    // set the result to an in-memory list of "the world"
    _ <- Task.gatherUnordered(z.map(cfg.repository.addInstance)).liftKleisli
    _  = log.info("added instances to the repository...")

    // from the whole world, figure out which are flask instances
    f  = l.filter(Deployed.isFlask)
    _  = log.info(s"found ${f.length} flasks in the running instance list...")

    // update the distribution with new capacity seeds
    _ <- Task.gatherUnordered(f.map(cfg.repository.increaseCapacity)).liftKleisli
    _  = log.debug("increased the known monitoring capactiy based on discovered flasks")

    // ask those flasks for their current work and yield a `Distribution`
    d <- Sharding.gatherAssignedTargets(f).liftKleisli
    _  = log.debug("read the existing state of assigned work from the remote instances")

    // update the distribution accordingly
    _ <- cfg.repository.mergeDistribution(d).liftKleisli
    _  = log.debug("merged the currently assigned work into the current distribution")

    _ <- (for {
      h <- Sharding.locateAndAssignDistribution(t, cfg.repository)
      g <- Sharding.distribute(h)
    } yield ()).liftKleisli

    _ <- Task.now(log.info(">>>>>>>>>>>> boostrap complete <<<<<<<<<<<<")).liftKleisli
  } yield ()

  /**
   * Initilize the chemist serivce by trying to create the various AWS resources
   * that are required to operate. Once complete, execute the boostrap.
   */
  lazy val init: Chemist[Unit] = {
    log.debug("attempting to read the world of deployed instances")
    for {
      cfg <- config

      // start to wire up the topics and subscriptions to queues
      a <- SNS.create(cfg.queue.topicName)(cfg.sns).liftKleisli
      _  = log.debug(s"created sns topic with arn = $a")

      b <- SQS.create(cfg.queue.topicName, a)(cfg.sqs).liftKleisli
      _  = log.debug(s"created sqs queue with arn = $b")

      c <- SNS.subscribe(a, b)(cfg.sns).liftKleisli
      _  = log.debug(s"subscribed sqs queue to the sns topic")

      // now the queues are setup with the right permissions,
      // start the lifecycle listener
      _ <- Lifecycle.run(cfg.queue.topicName, cfg.resources, Lifecycle.sink
            )(cfg.repository, cfg.sqs, cfg.asg, cfg.ec2).liftKleisli
      _  = log.debug("lifecycle process started")

      _ <- Task.delay(log.info(">>>>>>>>>>>> initilization complete <<<<<<<<<<<<")).liftKleisli
    } yield ()
  }

  //////////////////////// INTERNALS ////////////////////////////

  private val config: Chemist[ChemistConfig] =
    Kleisli.ask[Task, ChemistConfig]

  private def alterShard(id: InstanceID, state: AutoScalingEventKind): Chemist[Unit] =
    for {
      cfg <- config
      e  = AutoScalingEvent(id.toLowerCase, state)
      _ <- Lifecycle.event(e, cfg.resources)(cfg.repository, cfg.asg, cfg.ec2).liftKleisli
    } yield ()

  private def daemonThreads(name: String) = new ThreadFactory {
    def newThread(r: Runnable) = {
      val t = Executors.defaultThreadFactory.newThread(r)
      t.setDaemon(true)
      t.setName(name)
      t
    }
  }

  val defaultPool: ExecutorService =
    Executors.newFixedThreadPool(4, daemonThreads("chemist-thread"))

  val serverPool: ExecutorService =
    Executors.newCachedThreadPool(daemonThreads("chemist-server"))

  val schedulingPool: ScheduledExecutorService =
    Executors.newScheduledThreadPool(2, daemonThreads("chemist-scheduled-tasks"))

}
