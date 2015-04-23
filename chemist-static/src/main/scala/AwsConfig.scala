package funnel
package chemist
package static

import dispatch.Http
import knobs._

import concurrent.duration.Duration
import scalaz.concurrent.Task

case class StaticConfig(
  resources: List[String],
  network: NetworkConfig,
  commandTimeout: Duration,
  instances: Seq[Instance]
) extends PlatformConfig {
  val discovery: Discovery = new StaticDiscovery(instances: _*)
  val repository: Repository = new StatefulRepository(discovery)
  val http: Http = Http.configure(
    _.setAllowPoolingConnection(true)
     .setConnectionTimeoutInMs(commandTimeout.toMillis.toInt))
}

object Config {
  def readConfig(cfg: MutableConfig): StaticConfig = {
    val resources = cfg.require[List[String]]("chemist.resources-to-monitor").run
    val network   = cfg.subconfig("chemist.network")
    val timeout   = cfg.require[Duration]("chemist.command-timeout").run
    val instances = cfg.subconfig("chemist.instances")
    StaticConfig(resources, readNetwork(network), timeout, readInstances(instances))
  }

  private def readNetwork(cfg: MutableConfig): NetworkConfig =
    NetworkConfig(cfg.require[String]("host").run, cfg.require[Int]("port").run)

  private def readInstances(cfg: MutableConfig): Seq[Instance] = for {
    env   <- cfg.getEnv
    id    <- env.keys
    slot   = cfg.subconfig(id)
    u     <- slot.require("uri")
    uri    = new URI(u)
    i      = Instance(id, Location())
  } yield i
}
