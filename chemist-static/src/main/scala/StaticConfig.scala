package funnel
package chemist
package static

import java.net.URI

import dispatch.Http
import knobs._

import concurrent.duration.Duration
import scalaz._, Scalaz._
import scalaz.concurrent.{Strategy,Task}
import scalaz.stream.Process
import scalaz.stream.async.signalOf

case class StaticConfig(
  network: NetworkConfig,
  commandTimeout: Duration,
  targets: Map[TargetID, Set[Target]],
  flasks: Map[FlaskID, Flask]
) extends PlatformConfig {
  val discovery: Discovery = new StaticDiscovery(targets, flasks)
  val repository: Repository = new StatefulRepository
  val sharder = RandomSharding
  val http: Http = Http.configure(
    _.setAllowPoolingConnection(true)
     .setConnectionTimeoutInMs(commandTimeout.toMillis.toInt))
  val signal = signalOf(true)(Strategy.Executor(Chemist.serverPool))
  val remoteFlask = new HttpFlask(http, repository, signal)
  val templates = List.empty
  val maxInvestigatingRetries = 6
}

object Config {
  def readConfig(cfg: MutableConfig): Task[StaticConfig] = for {
    network     <- readNetwork(cfg.subconfig("chemist.network"))
    timeout     <- cfg.require[Duration]("chemist.command-timeout")
    subi        <- cfg.base.at("chemist.instances")
    subf        <- cfg.base.at("chemist.flasks")
    instances   =  readInstances(subi)
    flasks      =  readFlasks(subf)
  } yield StaticConfig(network, timeout, instances, flasks)

  private[static] def readNetwork(cfg: MutableConfig): Task[NetworkConfig] = for {
    host   <- cfg.require[String]("host")
    port   <- cfg.require[Int]("port")
  } yield NetworkConfig(host, port)

  def readLocation(cfg: Config): Location =
    Location(
      host             = cfg.require[String]("host"),
      port             = cfg.require[Int]("port"),
      datacenter       = cfg.require[String]("datacenter"),
      protocol         = cfg.lookup[String]("protocol"
        ).flatMap(NetworkScheme.fromString
        ).getOrElse(NetworkScheme.Http),
      isPrivateNetwork = true,
      intent = LocationIntent.fromString(
        cfg.require[String]("intent")
        ).getOrElse(LocationIntent.Mirroring),
      templates        = cfg.require[List[String]]("target-resource-templates").map(LocationTemplate)
    )

  private[static] def readFlasks(cfg: Config): Map[FlaskID, Flask] = {
    val ids: Vector[String] = cfg.env.keys.map(_.toString.split('.')(0)).toVector
    ids.toVector.map { id =>
      val loc = readLocation(cfg.subconfig(s"$id.location"))
      val locT = readLocation(cfg.subconfig(s"$id.telemetry"))
      FlaskID(id) -> Flask(FlaskID(id), loc, locT)
    }.toMap
  }

  private[static] def readInstances(cfg: Config): Map[TargetID, Set[Target]]= {
    val ids: Vector[String] = cfg.env.keys.map(_.toString.split('.')(0)).toVector
    ids.toVector.map { id =>
      val sub = cfg.subconfig(id)
      val cn = sub.require[String]("clusterName")
      val uris = sub.require[List[String]]("uris")
      TargetID(id) -> uris.map(u => Target(cn, new URI(u), false)).toSet
    }.toMap
  }
}
