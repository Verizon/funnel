package funnel
package chemist

import scalaz.concurrent.Task
import scalaz.syntax.kleisli._

class TestChemist extends Chemist[TestPlatform]{

  def bootstrap: ChemistK[Unit] =
    for {
      cfg <- config
    } yield ()

  def init: ChemistK[Unit] =
    Task.now(()).liftKleisli

}
