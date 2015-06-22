package funnel
package chemist

import unfiltered.request._
import unfiltered.response._
import unfiltered.netty._
import argonaut._, Argonaut._
import scalaz.concurrent.Task
import scalaz.stream.Process
import scalaz.{\/,-\/,\/-}
import journal.Logger

object JsonRequest {
  def apply[T](r: HttpRequest[T]) =
    new ParseWrap(r, new Parse[HttpRequest[T]] {
      def parse(req: HttpRequest[T]) = JsonParser.parse(Body.string(req))
    })
}

object JsonResponse {
  def apply[A: EncodeJson](a: A, params: PrettyParams = PrettyParams.nospace) =
    JsonContent ~>
      ResponseString(a.jencode.pretty(params))
}

object Server {
  private val log = Logger[Server.type]

  // there seems to be a bug in Task that makes doing what we previously had here
  // not possible. The server gets into a hang/deadlock situation.
  def unsafeStart[U <: Platform](chemist: Chemist[U], platform: U): Unit = {
    val repo = platform.config.repository
    val sharder = platform.config.sharder

    (repo.repoCommands to Process.constant(Sharding.handleRepoCommand(repo, sharder, platform.config.remoteFlask) _)).run.runAsync {
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

    val p = this.getClass.getResource("/oncue/www/")
    log.info(s"Setting web resource path to '$p'")

    unfiltered.netty.Server
      .http(platform.config.network.port, platform.config.network.host)
      .resources(p, cacheSeconds = 3600)
      .handler(Server(chemist, platform))
      .run
  }
}

@io.netty.channel.ChannelHandler.Sharable
case class Server[U <: Platform](chemist: Chemist[U], platform: U) extends cycle.Plan with cycle.SynchronousExecution with ServerErrorResponse {
  import JSON._
  import concurrent.duration._
  import chemist.ChemistK
  import Server._
  import metrics._

  private def json[A : EncodeJson](a: ChemistK[A]) =
    a(platform).attemptRun.fold(
      e => {
        log.error(s"Unable to process response: ${e.toString} - ${e.getMessage}")
        e.printStackTrace
        InternalServerError ~> JsonResponse(e.toString)
      },
      o => Ok ~> JsonResponse(o))

  private def decode[A : DecodeJson](req: HttpRequest[Any])(f: A => ResponseFunction[Any]) =
    JsonRequest(req).decodeEither[A].map(f).fold(fail => BadRequest ~> ResponseString(fail), identity)

  def intent = {
    case GET(Path("/")) =>
      GetRoot.time(Redirect("index.html"))

    case GET(Path("/status")) =>
      GetStatus.time(Ok ~> JsonResponse(Chemist.version))

    case GET(Path("/errors")) =>
      GetErrors.time(json(chemist.errors.map(_.toList)))

    case GET(Path("/distribution")) =>
      GetDistribution.time(json(chemist.distribution.map(_.toList)))

    case GET(Path("/unmonitorable")) =>
      json(chemist.unmonitorable)

    case GET(Path("/lifecycle/history")) =>
      GetLifecycleHistory.time(json(chemist.repoHistory.map(_.toList)))

    // the URI is needed internally, but does not make sense in the remote
    // user-facing api, so here we just ditch it and return the states.
    case GET(Path("/lifecycle/states")) =>
      GetLifecycleStates.time(json(chemist.states.map(_.toList.map {
        case (uri,state) => state })))

    case GET(Path("/platform/history")) =>
      GetLifecycleHistory.time(json(chemist.platformHistory.map(_.toList)))

    case POST(Path("/distribute")) =>
      PostDistribute.time(NotImplemented ~> JsonResponse("This feature is not avalible in this build. Sorry :-)"))

    case POST(Path("/bootstrap")) =>
      PostBootstrap.time(json(chemist.bootstrap))

    case GET(Path(Seg("shards" :: Nil))) =>
      GetShards.time(json(chemist.shards))

    case GET(Path(Seg("shards" :: id :: Nil))) =>
      GetShardById.time(json(chemist.shard(FlaskID(id))))

    case POST(Path(Seg("shards" :: id :: "exclude" :: Nil))) =>
      PostShardExclude.time(json(chemist.exclude(FlaskID(id))))

    case POST(Path(Seg("shards" :: id :: "include" :: Nil))) =>
      PostShardInclude.time(json(chemist.include(FlaskID(id))))

  }
}
