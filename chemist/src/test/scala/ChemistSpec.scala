package oncue.svc.laboratory

import journal.Logger

trait ChemistSpec {
  implicit lazy val log: Logger = Logger("chemist-spec")
}
