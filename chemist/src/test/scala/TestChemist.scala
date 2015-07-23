package funnel
package chemist

import scalaz.concurrent.Task
import scalaz.syntax.kleisli._
import scalaz.Applicative

class TestChemist extends Chemist[TestPlatform]{

  def filterTargets(instances: Seq[(TargetID, Set[Target])]): ChemistK[(Seq[(TargetID, Set[Target])], Seq[(TargetID, Set[Target])])] =
    Applicative[ChemistK].point((instances, Seq()))

  def init: ChemistK[Unit] =
    Task.now(()).liftKleisli

}
