package funnel
package chemist

import argonaut._, Argonaut._
import org.http4s._
import org.http4s.argonaut._
import scalaz.concurrent.Task
import scalaz.stream.Process
import scalaz.{\/,-\/,\/-}
import journal.Logger

object Server {
  private val log = Logger[Server.type]

  // there seems to be a bug in Task that makes doing what we previously had here
  // not possible. The server gets into a hang/deadlock situation.
  def unsafeStart[U <: Platform](chemist: Chemist[U], platform: U): Unit = {
    val repo = platform.config.repository

    (repo.repoCommands to Process.constant(Sharding.handleRepoCommand(repo, EvenSharding, platform.config.remoteFlask) _)).run.runAsync {
      case -\/(err) =>
        log.error(s"Error starting processing of Platform events: $err")
        err.printStackTrace

      case \/-(t)   => log.info(s"result of platform processing $t")
    }

    chemist.bootstrap(platform).runAsync {
      case -\/(err) =>
        log.error(s"Unable to bootstrap the chemist service. Failed with error: $err")
        err.printStackTrace

      case \/-(_)   => log.info("Sucsessfully bootstrap chemist at startup.")
    }

    chemist.init(platform).runAsync {
      case -\/(err) =>
        log.error(s"Unable to initilize the chemist service. Failed with error: $err")
        err.printStackTrace

      case \/-(_)   => log.info("Sucsessfully initilized chemist at startup.")
    }

    import org.http4s.server.staticcontent._
    import org.http4s.server.blaze._

    val p = resourceService(ResourceService.Config(
      basePath = "/oncue/www",
      executor = Chemist.serverPool,
      // (sic)
      cacheStartegy = MemoryCache()
    ))
    log.info(s"Setting web resource path to '${this.getClass.getResource("/oncue/www")}'")

    BlazeBuilder.bindHttp(platform.config.network.port)
      .mountService(Server(chemist, platform))
      .mountService(p)
      .run
      .awaitShutdown
  }

  def apply[U <: Platform](chemist: Chemist[U], platform: U) = {
    import JSON._
    import concurrent.duration._
    import chemist.ChemistK
    import metrics._
    import org.http4s.server._
    import org.http4s.dsl._

    def json[A : EncodeJson](a: ChemistK[A]) =
      a(platform).attemptRun.fold(
        e => {
          log.error(s"Unable to process response: ${e.toString} - ${e.getMessage}")
          e.printStackTrace
          InternalServerError(e.getMessage.asJson)
        },
        o => Ok(o.asJson))

    HttpService {
      case GET -> Root =>
        GetRoot.time(PermanentRedirect(Uri.uri("index.html")))

      case GET -> Root / "status" =>
        GetStatus.time(Ok(Chemist.version.asJson))

      case GET -> Root / "errors" =>
        GetStatus.time(json(chemist.errors.map(_.toList)))

      case GET -> Root / "distribution" =>
        GetDistribution.time(json(chemist.distribution.map(_.toList)))

      case GET -> Root / "lifecycle" / "history" =>
        GetLifecycleHistory.time(json(chemist.repoHistory.map(_.toList)))

      // the URI is needed internally, but does not make sense in the remote
      // user-facing api, so here we just ditch it and return the states.
      case GET -> Root / "lifecycle" / "states" =>
        GetLifecycleStates.time(json(chemist.states.map(_.toList.map(_._2))))

      case GET -> Root / "platform" / "history" =>
        GetLifecycleHistory.time(json(chemist.platformHistory.map(_.toList)))

      case POST -> Root / "distribute" =>
        PostDistribute.time(
          NotImplemented("This feature is not avalible in this build. Sorry :-)".asJson))

      case POST -> Root / "bootstrap" =>
        PostBootstrap.time(json(chemist.bootstrap))

      case GET -> Root / "shards" =>
        GetShards.time(json(chemist.shards))

      case GET -> Root / "shards" / id =>
        GetShardById.time(json(chemist.shard(FlaskID(id))))

      case POST -> Root / "shards" / id / "exclude" =>
        PostShardExclude.time(json(chemist.exclude(FlaskID(id))))

      case POST -> Root / "shards" / id / "include" =>
        PostShardInclude.time(json(chemist.include(FlaskID(id))))
    }
  }
}
