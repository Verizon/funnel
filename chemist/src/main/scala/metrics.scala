package funnel
package chemist

import funnel.instruments._

object metrics {
  // HTTP
  val GetRoot = timer("http/get/index", "time taken to get the root document")
  val GetStatus = timer("http/get/status", "time taken to get the version of Chemist")
  val GetErrors = timer("http/get/errors", "time taken to get the list of aggregated errors")
  val GetDistribution = timer("http/get/distribution", "time taken to get current work assignments of funnel -> flask")
  val GetLifecycleHistory = timer("http/get/lifecycle/history", "time taken to get last 100 lifecycle events this Chemist has seen")
  val GetUnmonitorable = timer("http/get/unmonitorable", "time taken to get all unmonitorable targets")
  val GetLifecycleStates = timer("http/get/lifecycle/states", "time taken to get all known funnels")
  val GetPlatformHistory = timer("http/get/platform/history", "time taken to get all the historical platform events")
  val PostDistribute = timer("http/post/distribute", "time taken to not implement this feature")
  val PostBootstrap = timer("http/post/bootstrap", "time taken to re-read the world from AWS")
  val GetShards = timer("http/get/shards", "time taken to list all the shards currently known by Chemist")
  val GetShardById = timer("http/get/shards/id", "time taken to get shard by ID")
  val PostShardExclude = timer("http/post/shards/id/exclude", "time taken to exclude shard by ID")
  val PostShardInclude = timer("http/post/shards/id/include", "time taken to include shard by ID")
  val GetShardDistribution = timer("http/get/shards/id/distribution", "time taken to list the distribution for a shard")
  val GetShardSources = timer("http/get/shards/id/sources", "time taken to get the sources for a shard")

  // hosts
  val AssignedHosts = numericGauge("hosts/assigned", 0.0, Units.Count, "number of hosts in Assigned state")
  val DoubleMonitoredHosts = numericGauge("hosts/doublemonitored", 0.0, Units.Count, "number of hosts in DoubleMonitored state")
  val UnknownHosts = numericGauge("hosts/unknown", 0.0, Units.Count, "number of hosts in Unknown state")
  val UnmonitoredHosts = numericGauge("hosts/unmonitored", 0.0, Units.Count, "number of hosts in Unmonitored state")
  val UnmonitorableHosts = numericGauge("hosts/unmonitorable", 0.0, Units.Count, "number of hosts in Unmonitorable state")
  val MonitoredHosts = numericGauge("hosts/monitored", 0.0, Units.Count, "number of hosts in Monitored state")
  val DoubleAssignedHosts = numericGauge("hosts/doubleassigned", 0.0, Units.Count, "number of hosts in DoubleAssigned state")
  val ProblematicHosts = numericGauge("hosts/problematic", 0.0, Units.Count, "number of hosts in Problematic state")
  val InvestigatingHosts = numericGauge("hosts/investigating", 0.0, Units.Count, "number of hosts in Investigating state")
  val FinHosts = numericGauge("hosts/fin", 0.0, Units.Count, "number of hosts in Fin state")

  // lifecycle
  val PlatformEventFailures = counter("lifecycle/platform-failures")
  val RepoEventsStream = trafficLight("lifecycle/repo-events")

  // telemetry
  val DroppedCommands = counter("telemetry/commands/dropped")

  // housekeeping
  val GatherAssignedLatency = lapTimer("housekeeping/gather-assigned-latency")
  val GatherUnassignedLatency = lapTimer("housekeeping/gather-unassigned-latency")
  val InvestigatingLatency = lapTimer("housekeeping/investigating-latency")

  // remote flask
  val CommandCount = counter("commands/total")
}
