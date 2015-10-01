package funnel
package chemist
package aws

import funnel.instruments._

object metrics {
  val LifecycleEvents = counter("lifecycle/events", 0, "number of lifecycle events within a given window")
  val Reshardings = counter("lifecycle/resharding", 0, "number of times lifecycle events triggered resharding")

  object discovery {
    val ListMonitorable = lapTimer("discovery/list/monitorable")
    val ListUnmonitorable = lapTimer("discovery/list/unmonitorable")
    val ListFlasks = lapTimer("discovery/list/flasks")
    val LookupManyAws = lapTimer("discovery/lookup/aws")
    val ValidateLatency = lapTimer("discovery/validate")
  }
}
