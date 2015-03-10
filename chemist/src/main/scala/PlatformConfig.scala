package funnel
package chemist

trait PlatformConfig {
  def resources: List[String]
  def network: NetworkConfig
  def discovery: Discovery
  def repository: Repository
}
