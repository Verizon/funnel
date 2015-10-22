package funnel
package integration

import scalaz.concurrent.Strategy
import scalaz.stream.async.signalOf
import chemist._
import dispatch.Http
import scala.concurrent.duration._

class IntegrationConfig extends PlatformConfig {
  val templates: List[LocationTemplate] =
    List(LocationTemplate("http://@host:@port/stream/previous"))
  val network = NetworkConfig("127.0.0.1",64529)
  val discovery: Discovery = new IntegrationDiscovery
  val http = Http.configure(_.setAllowPoolingConnection(true).setConnectionTimeoutInMs(30000))
  val sharder: chemist.Sharder = RandomSharding
  val remoteFlask: RemoteFlask = new HttpFlask(http)
  val state = chemist.MemoryStateCache
}
