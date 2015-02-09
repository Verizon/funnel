package oncue.svc.funnel
package agent
package jmx

import cjmx.util.jmx.Attach

object Poller {
  def listVMs = Attach.localVMIDs
}
