package funnel
package chemist
package aws

import knobs.{loadImmutable,Required,FileResource,ClassPathResource}
import java.io.File
import java.net.{ InetSocketAddress, Socket, URI }
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
   * used to stop our sockets listening to telemetry on all the flasks
   */
  private val signal: Signal[Boolean] = signalOf(true)

  /**
   * Goal of this function is to validate that the machine instances specified
   * by the supplied group `g`, are in fact running a funnel instance and it is
   * ready to start sending metrics if we connect to its `/stream` function.
   */
  private[aws] def validate(target: Target): Task[Target] = {
    /**
     * Do a naieve check to see if the socket is even network accessible.
     * Given that funnel is using multiple protocols we can't assume that
     * any given protocol at this point in time, so we just try to see if
     * its even avalible on the port the discovery flow said it was.
     *
     * This mainly guards against miss-configuration of the network setup,
     * LAN-ACLs, firewalls etc.
     */
    def go(uri: URI): Throwable \/ Unit =
      \/.fromTryCatchThrowable[Unit, Exception]{
        val s = new Socket
        // timeout in 300ms to keep the overhead reasonable
        try s.connect(new InetSocketAddress(uri.getHost, uri.getPort), 300)
        finally s.close // whatever the outcome, close the socket to prevent leaks.
      }

    for {
      a <- Task(go(target.uri))(Chemist.defaultPool)
      b <- a.fold(e => Task.fail(e), o => Task.now(o))
    } yield target
  }

  /**
   * if the config says to include vpc targets, include them if the target is in a private network
   * if the target is on a public network address, include it
   * otherwise, wtf, how did we arrive here - dont monitor it.
   */
  def filterTargets(instances: Seq[(TargetID, Set[Target])]): ChemistK[(Seq[(TargetID, Set[Target])], Seq[(TargetID, Set[Target])])] =
    config.map { cfg =>
      val split = instances.map {
        case (id, targets) =>
          id -> targets.partition { t =>
            validate(t).attemptRun.isRight
          }
      }
      val in = split.collect {
        case (id, (good, bad)) if good.nonEmpty => id -> good
      }
      val out = split.collect {
        case (id, (good, bad)) if bad.nonEmpty => id -> bad
      }
      (in, out)
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
      _ <- Lifecycle.run(cfg.queue.topicName, signalOf(true)
            )(cfg.repository, cfg.sqs, cfg.asg, cfg.ec2, cfg.discovery).liftKleisli
      _  = log.debug("lifecycle process started")

      _ <- Task.delay(log.info(">>>>>>>>>>>> initilization complete <<<<<<<<<<<<")).liftKleisli
    } yield ()
  }
}
