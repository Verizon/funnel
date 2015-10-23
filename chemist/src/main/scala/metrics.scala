package funnel
package chemist

import funnel.instruments._

object metrics {
  // HTTP
  val GetRoot = timer("http/get/index", "time taken to get the root document")
  val GetStatus = timer("http/get/status", "time taken to get the version of Chemist")
  val GetDistribution = timer("http/get/distribution", "time taken to get current work assignments of funnel -> flask")
  val GetUnmonitorable = timer("http/get/unmonitorable", "time taken to get all unmonitorable targets")
  val GetPlatformHistory = timer("http/get/platform/history", "time taken to get all the historical platform events")
  val PostDistribute = timer("http/post/distribute", "time taken to not implement this feature")
  val GetShards = timer("http/get/shards", "time taken to list all the shards currently known by Chemist")
  val GetShardById = timer("http/get/shards/id", "time taken to get shard by ID")
  val PostShardExclude = timer("http/post/shards/id/exclude", "time taken to exclude shard by ID")
  val PostShardInclude = timer("http/post/shards/id/include", "time taken to include shard by ID")
  val GetShardDistribution = timer("http/get/shards/id/distribution", "time taken to list the distribution for a shard")
  val GetShardSources = timer("http/get/shards/id/sources", "time taken to get the sources for a shard")

  // housekeeping
  val GatherAssignedLatency = lapTimer("housekeeping/gather-assigned-latency")
  val GatherUnassignedLatency = lapTimer("housekeeping/gather-unassigned-latency")

  // remote flask
  val CommandCount = counter("commands/total")
}
