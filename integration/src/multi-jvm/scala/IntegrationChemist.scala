package funnel
package integration

import chemist.{Chemist,Target,TargetID}
import scalaz.{Applicative,Scalaz}
import scalaz.concurrent.Task
import journal.Logger

class IntegrationChemist extends Chemist[IntegrationPlatform]{
  import Scalaz._
  val log = Logger[IntegrationChemist]

  def filterTargets(instances: Seq[(TargetID, Set[Target])]): ChemistK[(Seq[(TargetID, Set[Target])], Seq[(TargetID, Set[Target])])] =
    Applicative[ChemistK].point((instances, Seq()))

  def init: ChemistK[Unit] = {
    log.info("Initilizing Chemist....")
    for {
      c <- config
      _ <- Task.now(c.statefulRepository.lifecycle()).liftKleisli
    } yield ()
  }
}
