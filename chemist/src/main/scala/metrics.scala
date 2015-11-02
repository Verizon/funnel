package funnel
package chemist

import funnel.instruments._

object metrics {
  val GatherAssignedLatency = lapTimer("pipeline/gather-assigned-latency")
  val MonitorCommandLatency = lapTimer("commands/monitor")
  val UnmonitorCommandLatency = lapTimer("commands/unmonitor")
}
