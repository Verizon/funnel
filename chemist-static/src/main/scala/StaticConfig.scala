package funnel
package chemist
package static

import java.net.URI

import dispatch.Http
import knobs._

import concurrent.duration.Duration
import scalaz._, Scalaz._
import scalaz.concurrent.Task
import scalaz.stream.Process

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
  def readConfig(cfg: MutableConfig): Task[StaticConfig] = for {
    resources   <- cfg.require[List[String]]("chemist.resources-to-monitor")
    network     <- readNetwork(cfg.subconfig("chemist.network"))
    timeout     <- cfg.require[Duration]("chemist.command-timeout")
    sub         <- cfg.base.at("chemist.instances")
    instances   <- readInstances(sub)
  } yield StaticConfig(resources, network, timeout, instances)

  private[static] def readNetwork(cfg: MutableConfig): Task[NetworkConfig] = for {
    host   <- cfg.require[String]("host")
    port   <- cfg.require[Int]("port")
  } yield NetworkConfig(host, port)

  private[static] def readInstances(cfg: Config) = (for {
      id    <- Process.eval(Task(cfg.env.keys.toSeq)).flatMap(Process.emitAll)
      u     <- Process.eval(Task(cfg.require[String](id)))
      uri    = new URI(u)
    } yield Instance(id.split(".")(0), Location(Option(uri.getHost), "", uri.getPort, "", false), List(), Map())).runLog
}
