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

class AwsChemist[A <: Aws] extends Chemist[A]{
  private val log = Logger[AwsChemist[_]]

  /**
   * Initilize the chemist serivce by trying to create the various AWS resources
   * that are required to operate. Once complete, execute the init.
   *
   * There are a couple of important things to note here:
   * 1. whilst chemist will try to create an SNS topic with the specified name
   *    this still needs to be connected with your ASGs in whatever deployment
   *    configuration you happen to be using (e.g. cloudformation). This is
   *    inherently out-of-band for chemist, so we just "create" the SNS topic
   *    to ensure cheimst has the best chance of initilizing correctly. Without
   *    the aforementioend external configuration, you wont ever get any lifecycle
   *    events, and subsequently no immediete monitoring.
   *
   * 2. Given that SQS is basically a mutable queue, it does not play well when
   *    there are multiple chemist instances running. In an effort to isolate ourselves
   *    from this, the expectation is that you would be launching chemist with
   *    cloudformation and specifiying the relevant queue as an output of your template.
   *    Doing this means that every deployment you do has a its own, isolated mutable
   *    queue to work with, and when the CFN stack is deleted, the queue will be too.
   */
  lazy val init: ChemistK[Unit] = {
    log.debug("attempting to read the world of deployed instances")
    for {
      cfg <- config

      // start to wire up the topics and subscriptions to queues
      a <- SNS.create(cfg.queue.topicName)(cfg.sns).liftKleisli
      _  = log.debug(s"created sns topic with arn = $a")

      b <- cfg.discovery.lookupOne(cfg.machine.id).liftKleisli
      b1 = b.tags.get("aws:cloudformation:stack-name").getOrElse("unknown")
      _  = log.debug(s"discovered stack name for this running instance to be '$b1'")

      c <- CFN.getStackOutputs(b1)(cfg.cfn).liftKleisli
      c1 = c.get("ServiceQueueARN").getOrElse("unknown")
      _  = log.debug(s"discovered sqs queue with name '$c1'")

      _ <- SNS.subscribe(a, c1)(cfg.sns).liftKleisli
      _  = log.debug(s"subscribed sqs queue to the sns topic")

      // now the queues are setup with the right permissions,
      // start the lifecycle listener
      _ <- Pipeline.task(
             Lifecycle.stream(cfg.queue.topicName
               )(cfg.sqs, cfg.asg, cfg.ec2, cfg.discovery
               ).map(Pipeline.contextualise),
             cfg.rediscoveryInterval
           )(cfg.discovery, cfg.sharder, sinks.caching(cfg.state), sinks.unsafeNetworkIO).liftKleisli

      _  = log.debug("lifecycle process started")

      _ <- Task.delay(log.info(">>>>>>>>>>>> initilization complete <<<<<<<<<<<<")).liftKleisli
    } yield ()
  }
}
