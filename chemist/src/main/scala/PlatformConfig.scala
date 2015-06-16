package funnel
package chemist

import org.http4s.client.Client

trait PlatformConfig {
  def resources: List[String]
  def network: NetworkConfig
  def discovery: Discovery
  def repository: Repository
  def sharder: Sharder
  def remoteFlask: RemoteFlask
  def http: Client
}
