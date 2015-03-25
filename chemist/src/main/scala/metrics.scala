package funnel
package chemist

import funnel.instruments._

object metrics {
  val GetRoot = timer("http/get/index", "time taken to get the root document")
  val GetStatus = timer("http/get/status", "time taken to get the version of Chemist")
  val GetDistribution = timer("http/get/distribution", "time taken to get current work assignments of funnel -> flask")
  val GetLifecycleHistory = timer("http/get/lifecycle/history", "time taken to get last 100 lifecycle events this Chemist has seen")
  val PostDistribute = timer("http/post/distribute", "time taken to not implement this feature")
  val PostBootstrap = timer("http/post/bootstrap", "time taken to re-read the world from AWS")
  val GetShards = timer("http/get/shards", "time taken to list all the shards currently known by Chemist")
  val GetShardById = timer("http/get/shards/id", "time taken to get shard by ID")
  val PostShardExclude = timer("http/post/shards/id/exclude", "time taken to exclude shard by ID")
  val PostShardInclude = timer("http/post/shards/id/include", "time taken to include shard by ID")

  val LifecycleEvents = counter("lifecycle/events", 0, "number of lifecycle events within a given window")
  val Reshardings = counter("lifecycle/resharding", 0, "number of times lifecycle events triggered resharding")
}
