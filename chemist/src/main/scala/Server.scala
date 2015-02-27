package funnel
package chemist

import unfiltered.request._
import unfiltered.response._
import unfiltered.netty._
import argonaut._, Argonaut._
import scalaz.concurrent.Task
import journal.Logger

object JsonRequest {
  def apply[T](r: HttpRequest[T]) =
    new ParseWrap(r, new Parse[HttpRequest[T]] {
      def parse(req: HttpRequest[T]) = JsonParser.parse(Body.string(req))
    })
}

object JsonResponse {
  def apply[A: EncodeJson](a: A, params: PrettyParams = PrettyParams.nospace) =
    JsonContent ~> ResponseString(a.jencode.pretty(params))
}

object Server {
  private val log = Logger[Server.type]

  def start(cfg: ChemistConfig): Task[Unit] =
    Task.reduceUnordered[Unit, Unit](Seq(
      Chemist.init(cfg),
      Task(unfiltered.netty.Server
        .http(cfg.network.port, cfg.network.host)
        .resources(getClass.getResource("/oncue/www/"), cacheSeconds = 3600)
        .handler(Server(cfg))
        .run
      )
    ))

    // .runAsync(_.fold(
    //   e => log.error(s"Problem occoured during server initilization: $e"),
    //   s => log.warn("Background process completed sucsessfully. This may have happened in error, as typically the process matches the lifecycle of the server.")
    // ))
}

@io.netty.channel.ChannelHandler.Sharable
case class Server(cfg: ChemistConfig) extends cycle.Plan with cycle.SynchronousExecution with ServerErrorResponse {
  import JSON._
  import concurrent.duration._

  private def json[A : EncodeJson](a: Chemist[A]) =
    a(cfg).attemptRun.fold(
      e => InternalServerError ~> JsonResponse(e.toString),
      o => Ok ~> JsonResponse(o))

  private def decode[A : DecodeJson](req: HttpRequest[Any])(f: A => ResponseFunction[Any]) =
    JsonRequest(req).decodeEither[A].map(f).fold(fail => BadRequest ~> ResponseString(fail), identity)

  def intent = {
    case GET(Path("/")) =>
      Redirect("/index.html")

    case GET(Path("/status")) =>
      Ok ~> JsonResponse(Chemist.version)

    case GET(Path("/distribution")) =>
      json(Chemist.distribution.map(_.toList))

    case GET(Path("/lifecycle/history")) =>
      json(Chemist.history.map(_.toList))

    case POST(Path("/distribute")) =>
      NotImplemented ~> JsonResponse("This feature is not avalible in this build. Sorry :-)")

    case POST(Path("/bootstrap")) =>
      json(Chemist.bootstrap)

    case GET(Path(Seg("shards" :: Nil))) =>
      json(Chemist.shards)

    case GET(Path(Seg("shards" :: id :: Nil))) =>
      json(Chemist.shard(id))

    case POST(Path(Seg("shards" :: id :: "exclude" :: Nil))) =>
      json(Chemist.exclude(id))

    case POST(Path(Seg("shards" :: id :: "include" :: Nil))) =>
      json(Chemist.include(id))

  }
}
