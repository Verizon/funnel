package funnel
package chemist

import dispatch.Http

trait PlatformConfig {
  def templates: List[LocationTemplate]
  def network: NetworkConfig
  def discovery: Discovery
  def sharder: Sharder
  def remoteFlask: RemoteFlask
  def http: Http
  def maxInvestigatingRetries: Int
  def caches: StateCache
}
