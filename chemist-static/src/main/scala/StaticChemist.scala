package funnel
package chemist
package static

import knobs.{loadImmutable,Required,FileResource,ClassPathResource}
import java.io.File
import journal.Logger
import scalaz.{\/,-\/,\/-,Kleisli}
import scalaz.syntax.kleisli._
import scalaz.concurrent.Task
import java.util.concurrent.{Executors, ExecutorService, ScheduledExecutorService, ThreadFactory}

object StaticChemist {
  /**
   * filter out all the flask instances; these are not targets.
   * if the target is on a public network address, include it
   * otherwise, wtf, how did we arrive here - dont monitor it.
   */
/*
  def filterInstances(instances: Seq[Instance])(cfg: StaticConfig): Seq[Instance] =
    instances.filterNot(cfg.discovery.isFlask).collect {
      case b if (!b.location.isPrivateNetwork) => b
    }
*/
}

class StaticChemist extends Chemist[Static]{

  val log = Logger[this.type]

  /**
   * Force chemist to re-read the world from AWS. Useful if for some reason
   * Chemist gets into a weird state at runtime.
   */
  def bootstrap: ChemistK[Unit] = for {
    cfg <- config
/*
    // read the list of all deployed machines
    l <- cfg.discovery.list.liftKleisli
    _  = log.info(s"found a total of ${l.length} deployed, accessable instances...")

    // filter out all the instances that are in private networks
    // TODO: support VPCs by dynamically determining if chemist is in a vpc itself
    z  = AwsChemist.filterInstances(l)(cfg)
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
    d <- Sharding.gatherAssignedTargets(f)(cfg.http).liftKleisli
    _  = log.debug("read the existing state of assigned work from the remote instances")

    // update the distribution accordingly
    _ <- cfg.repository.mergeDistribution(d).liftKleisli
    _  = log.debug("merged the currently assigned work into the current distribution")

    _ <- (for {
      h <- Sharding.locateAndAssignDistribution(t, cfg.repository)
      g <- Sharding.distribute(h)(cfg.http)
    } yield ()).liftKleisli

    _ <- Task.now(log.info(">>>>>>>>>>>> boostrap complete <<<<<<<<<<<<")).liftKleisli
 */
  } yield ()

  /**
   * Initilize the chemist serivce by trying to create the various resources
   * that are required to operate. Once complete, execute the boostrap.
   */
  lazy val init: ChemistK[Unit] = {
    log.debug("attempting to read the world of deployed instances")
    for {
      cfg <- config

      _ <- Task.delay(log.info(">>>>>>>>>>>> initilization complete <<<<<<<<<<<<")).liftKleisli
    } yield ()
  }
}
