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

class AwsChemist extends Chemist[Aws]{

  val log = Logger[this.type]

  /**
   * used to stop our sockets listening to telemetry on all the flasks
   */
  private val signal: Signal[Boolean] = signalOf(true)


  /**
   * if the config says to include vpc targets, include them if the target is in a private network
   * if the target is on a public network address, include it
   * otherwise, wtf, how did we arrive here - dont monitor it.
   */
  def filterTargets(instances: Seq[(TargetID, Set[Target])]): ChemistK[Seq[(TargetID, Set[Target])]] =
    config.map { cfg =>
      instances.map {
        case (id, targets) =>
          id -> targets.collect {
            case b if cfg.includeVpcTargets => b
            case b if (!b.isPrivateNetwork) => b

          }
      }.filter(_._2.nonEmpty)
    }

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
