package funnel
package chemist

class TestConfig extends PlatformConfig {
  val resources: List[String] = List("stream/previous")
  val network = NetworkConfig("127.0.0.1",64523)
  val discovery: Discovery = new TestDiscovery
  val repository: Repository = new StatefulRepository(discovery)
}
