package oncue.svc.funnel.chemist

import journal.Logger

trait ChemistSpec {
  implicit lazy val log: Logger = Logger("chemist-spec")
}
