package funnel
package chemist

import scalaz.stream.async
import scalaz.stream.async.mutable.Signal

class TestConfig extends PlatformConfig {
  val resources: List[String] = List("stream/previous")
  val network = NetworkConfig("127.0.0.1",64523)
  val discovery: Discovery = new TestDiscovery
  val repository: Repository = new StatefulRepository
}
