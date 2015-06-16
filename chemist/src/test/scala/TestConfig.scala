package funnel
package chemist

import scalaz.stream.async
import scalaz.stream.async.mutable.Signal
import org.http4s.client._

class TestConfig extends PlatformConfig {
  val resources: List[String] = List("stream/previous")
  val network = NetworkConfig("127.0.0.1",64523)
  val discovery: Discovery = new TestDiscovery
  val repository: Repository = new StatefulRepository
  val remoteFlask: RemoteFlask = LoggingRemote
  def http: Client = ???
  val sharder: funnel.chemist.Sharder = EvenSharding
}
