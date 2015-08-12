package funnel
package chemist

import scalaz.stream.async
import scalaz.stream.async.mutable.Signal

class TestConfig extends PlatformConfig {
  val templates: List[LocationTemplate] =
    List(LocationTemplate("http://@host:@port/stream/previous"))
  val network = NetworkConfig("127.0.0.1",64523)
  val discovery: Discovery = new TestDiscovery
  val repository: Repository = new StatefulRepository
  val remoteFlask: RemoteFlask = LoggingRemote
  def http: dispatch.Http = ???
  val sharder: Sharder = RandomSharding
  def election: ElectionStrategy = ???
  val maxInvestigatingRetries = 6
}
