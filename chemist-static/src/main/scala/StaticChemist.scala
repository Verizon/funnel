package funnel
package chemist
package static

import knobs.{loadImmutable,Required,FileResource,ClassPathResource}
import java.io.File
import journal.Logger
import scalaz.{\/,-\/,\/-,Kleisli}
import scalaz.syntax.kleisli._
import scalaz.concurrent.Task
import scalaz.Applicative
import java.util.concurrent.{Executors, ExecutorService, ScheduledExecutorService, ThreadFactory}

class StaticChemist extends Chemist[Static]{

  val log = Logger[this.type]

  def filterTargets(instances: Seq[(TargetID, Set[Target])]): ChemistK[(Seq[(TargetID, Set[Target])], Seq[(TargetID, Set[Target])])] = Applicative[ChemistK].point((instances, Seq()))

  /* Initilize the chemist serivce by trying to create the various resources
   * that are required to operate. Once complete, execute the boostrap. */
  lazy val init: ChemistK[Unit] = {
    log.debug("attempting to read the world of deployed instances")
    for {
      cfg <- config
      _ <- Task.delay(log.info(">>>>>>>>>>>> initilization complete <<<<<<<<<<<<")).liftKleisli
    } yield ()
  }
}
