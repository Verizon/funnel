package funnel
package chemist
package aws

import knobs.{loadImmutable,Required,FileResource,ClassPathResource}
import java.io.File
import journal.Logger
import scalaz.{\/,-\/,\/-,Kleisli}
import scalaz.syntax.kleisli._
import scalaz.concurrent.Task
import java.util.concurrent.{Executors, ExecutorService, ScheduledExecutorService, ThreadFactory}

class AwsChemist extends Chemist[Aws]{
  import Sharding.Target

  val log = Logger[this.type]

  /**
   * Force chemist to re-read the world from AWS. Useful if for some reason
   * Chemist gets into a weird state at runtime.
   */
  def bootstrap: ChemistK[Unit] = for {
    cfg <- config
    // read the list of all deployed machines
    l <- cfg.discovery.list.liftKleisli
    _  = log.info(s"found a total of ${l.length} deployed, accessable instances...")

    // filter out all the instances that are in private networks
    // TODO: support VPCs by dynamically determining if chemist is in a vpc itself
    z  = l.filterNot(_.location.isPrivateNetwork)
          .filterNot(cfg.discovery.isFlask)
    _  = log.info(s"located ${z.length} instances that appear to be monitorable")

    // convert the instance list into reachable targets
    t  = z.flatMap(Target.fromInstance(cfg.resources)).toSet
    _  = log.debug(s"targets are: $t")

    // set the result to an in-memory list of "the world"
    _ <- Task.gatherUnordered(z.map(cfg.repository.addInstance)).liftKleisli
    _  = log.info("added instances to the repository...")

    // from the whole world, figure out which are flask instances
    f  = l.filter(cfg.discovery.isFlask)
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
      _ <- Lifecycle.run(cfg.queue.topicName, cfg.resources, Lifecycle.sink
            )(cfg.repository, cfg.sqs, cfg.asg, cfg.ec2, cfg.discovery).liftKleisli
      _  = log.debug("lifecycle process started")

      _ <- Task.delay(log.info(">>>>>>>>>>>> initilization complete <<<<<<<<<<<<")).liftKleisli
    } yield ()
  }

  protected def alterShard(id: InstanceID, state: AutoScalingEventKind): ChemistK[Unit] =
    for {
      cfg <- config
      e  = AutoScalingEvent(id.toLowerCase, state)
      _ <- Lifecycle.event(e, cfg.resources)(cfg.repository, cfg.asg, cfg.ec2, cfg.discovery).liftKleisli
    } yield ()
}
