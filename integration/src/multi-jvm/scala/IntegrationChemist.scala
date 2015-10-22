package funnel
package integration

import chemist.Chemist
import scalaz.Scalaz
import scalaz.concurrent.Task

class IntegrationChemist extends Chemist[IntegrationPlatform]{
  import Scalaz._

  private[this] val log = journal.Logger[IntegrationChemist]

  def init: ChemistK[Unit] = {
    log.info("Initilizing Chemist....")
    Task.now(()).liftKleisli
  }
}
