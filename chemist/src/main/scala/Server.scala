package funnel
package chemist

import unfiltered.request._
import unfiltered.response._
import unfiltered.netty._
import argonaut._, Argonaut._
import scalaz.concurrent.Task

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
  def start(cfg: ChemistConfig): Unit =
    Task.reduceUnordered[Unit, Unit](Seq(
      Chemist.init(cfg),
      Task(unfiltered.netty.Server.http(cfg.network.port, cfg.network.host)
                                  .handler(Server(cfg))
                                  .run)))
}

@io.netty.channel.ChannelHandler.Sharable
case class Server(cfg: ChemistConfig) extends cycle.Plan with cycle.SynchronousExecution with ServerErrorResponse {
  import JSON._
  import concurrent.duration._

  private def json[A : EncodeJson](a: Chemist[A], cfg: ChemistConfig) =
    a(cfg).attemptRun.fold(
      e => InternalServerError ~> JsonResponse(e.toString),
      o => Ok ~> JsonResponse(o))

  private def decode[A : DecodeJson](req: HttpRequest[Any])(f: A => ResponseFunction[Any]) =
    JsonRequest(req).decodeEither[A].map(f).fold(fail => BadRequest ~> ResponseString(fail), identity)

  def intent = {
    case GET(Path("/status")) => Ok
    case GET(Path("/distribution")) => Ok //json(Chemist.distribution, cfg)
    case GET(Path("/lifecycle/history")) => Ok
    case POST(Path("/distribute")) => Ok
    case POST(Path("/bootstrap")) => Ok
    case GET(Path(Seg("shards" :: Nil))) => Ok
    case GET(Path(Seg("shards" :: id :: Nil))) => Ok
    case POST(Path(Seg("shards" :: id :: "exclude" :: Nil))) => Ok
    case POST(Path(Seg("shards" :: id :: "include" :: Nil))) => Ok
    case _ => NotFound
  }
}


// object Server0 extends Server {
//   init().runAsync(_.fold(
//     e => log.error(s"Problem occoured during server initilization: $e"),
//     s => log.warn("Background process completed sucsessfully. This may have happened in error, as typically the process matches the lifecycle of the server.")
//   ))
// }

