package funnel
package integration

import chemist.{Chemist,Target,TargetID}
import scalaz.{Applicative,Scalaz}
import scalaz.concurrent.Task

class IntegrationChemist extends Chemist[IntegrationPlatform]{
  import Scalaz._

  def filterTargets(instances: Seq[(TargetID, Set[Target])]): ChemistK[Seq[(TargetID, Set[Target])]] =
    Applicative[ChemistK].point(instances)

  def init: ChemistK[Unit] =
    for {
      c <- config
      _ <- Task.now(c.statefulRepository.lifecycle()).liftKleisli
    } yield ()

    // Task.now(config).liftKleisli
}
