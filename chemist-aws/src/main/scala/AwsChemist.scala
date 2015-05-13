package funnel
package chemist
package aws

import knobs.{loadImmutable,Required,FileResource,ClassPathResource}
import java.io.File
import journal.Logger
import scalaz.{\/,-\/,\/-,Kleisli}
import scalaz.syntax.kleisli._
import scalaz.concurrent.Task
import scalaz.std.vector._
import scalaz.syntax.traverse._
import scalaz.syntax.id._
import scalaz.stream.async.signalOf
import scalaz.stream.async.mutable.Signal
import java.util.concurrent.{Executors, ExecutorService, ScheduledExecutorService, ThreadFactory}
import funnel.aws._

object AwsChemist {
  /**
   * filter out all the flask instances; these are not targets.
   * if the config says to include vpc targets, include them if the target is in a private network
   * if the target is on a public network address, include it
   * otherwise, wtf, how did we arrive here - dont monitor it.
   */
  def filterInstances(instances: Seq[(TargetID, Set[Target])])(cfg: AwsConfig): Seq[(TargetID, Set[Target])] =
    instances.map {
      case (id, targets) =>
        id -> targets.collect {
          case b if cfg.includeVpcTargets => b
          case b if (!b.isPrivateNetwork) => b
        }
    }.filter(_._2.nonEmpty)
}

class AwsChemist extends Chemist[Aws]{

  val log = Logger[this.type]

  /**
   * used to stop our sockets listening to telemetry on all the flasks
   */
  private val signal: Signal[Boolean] = signalOf(true)

  /**
   * Force chemist to re-read the world from AWS. Useful if for some reason
   * Chemist gets into a weird state at runtime.
   */
  def bootstrap: ChemistK[Unit] = for {
    cfg <- config

    // from the whole world, figure out which are flask instances
    f  <- cfg.discovery.listFlasks.liftKleisli
    _  = log.info(s"found ${f.length} flasks in the running instance list...")

    // update the distribution with new capacity seeds
    _ <- f.toVector.traverse_(flask => PlatformEvent.NewFlask(flask) |> Sharding.platformHandler(cfg.repository)).liftKleisli
    _  = log.debug("increased the known monitoring capactiy based on discovered flasks")

    // read the list of all deployed machines
    l <- cfg.discovery.listTargets.liftKleisli
    _  = log.info(s"found a total of ${l.length} deployed, accessable instances...")

    // filter out all the instances that are in private networks
    // TODO: support VPCs by dynamically determining if chemist is in a vpc itself
    z  = AwsChemist.filterInstances(l)(cfg)
    _  = log.info(s"located ${z.length} instances that appear to be monitorable")

    // set the result to an in-memory list of "the world"
    targets = z.flatMap { case (id,targets) => targets.toSeq.map(PlatformEvent.NewTarget) } //the fact that I'm throwing ID away here is suspect
    _ <- targets.toVector.traverse_(Sharding.platformHandler(cfg.repository)).liftKleisli
    _  = log.info("added instances to the repository...")

/* STU: is this taken care of by housekeeping yet?
    // ask those flasks for their current work and yield a `Distribution`
    d <- Sharding.gatherAssignedTargets(f)(cfg.http).liftKleisli
    _  = log.debug("read the existing state of assigned work from the remote instances")

    // update the distribution accordingly
    _ <- cfg.repository.mergeDistribution(d).liftKleisli
    _  = log.debug("merged the currently assigned work into the current distribution")

    _ <- (for {
      h <- Sharding.locateAndAssignDistribution(t, cfg.repository)
      g <- Sharding.distribute(h)(cfg.http)
    } yield ()).liftKleisli
 */
    _ <- Task.now(log.info(">>>>>>>>>>>> boostrap complete <<<<<<<<<<<<")).liftKleisli

  } yield ()

  /**
   * Initilize the chemist serivce by trying to create the various AWS resources
   * that are required to operate. Once complete, execute the boostrap.
   */
  lazy val init: ChemistK[Unit] = {
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
      _ <- Lifecycle.run(cfg.queue.topicName, cfg.resources, signalOf(true)
            )(cfg.repository, cfg.sqs, cfg.asg, cfg.ec2, cfg.discovery).liftKleisli
      _  = log.debug("lifecycle process started")

      _ <- Task.delay(log.info(">>>>>>>>>>>> initilization complete <<<<<<<<<<<<")).liftKleisli
    } yield ()
  }
}
