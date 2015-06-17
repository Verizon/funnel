package funnel
package integration

import scalaz.concurrent.Strategy
import scalaz.stream.async.signalOf
import chemist._
import org.http4s.client._
import scala.concurrent.duration._

class IntegrationConfig extends PlatformConfig {
  val resources: List[String] = List("stream/previous")
  val network = NetworkConfig("127.0.0.1",64529)
  val discovery: Discovery = new IntegrationDiscovery
  val statefulRepository: StatefulRepository = new StatefulRepository
  val repository: Repository = statefulRepository
  val http: Client = blaze.PooledHttp1Client(timeout = 50.milliseconds)
  val signal = signalOf(true)(Strategy.Executor(Chemist.serverPool))
  val sharder: funnel.chemist.Sharder = EvenSharding
  val remoteFlask: RemoteFlask = new HttpFlask(http, repository, signal)
}
