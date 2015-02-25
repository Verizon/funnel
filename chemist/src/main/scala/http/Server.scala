package funnel
package chemist
package http

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

@io.netty.channel.ChannelHandler.Sharable
class Server(I: Instruments)(cfg: ChemistConfig) extends cycle.Plan with cycle.SynchronousExecution with ServerErrorResponse {
  import JSON._
  import concurrent.duration._

  // private def run[A : EncodeJson](
  //   exe: Free[Server.ServerF, A],
  //   req: HttpExchange
  // ): Unit = {
  //   I.run(exe).attemptRun match {
  //     case \/-(a) => flush(200, a.asJson.nospaces, req)
  //     case -\/(e) => flush(500, e.toString, req)
  //   }
  // }

  // private def json[A : EncodeJson](a: Gong[A], cfg: GongConfig) =
  //   Ok ~> JsonResponse(a(cfg).run)

  private def decode[A : DecodeJson](req: HttpRequest[Any])(f: A => ResponseFunction[Any]) =
    JsonRequest(req).decodeEither[A].map(f).fold(fail => BadRequest ~> ResponseString(fail), identity)

  def intent = {
    case GET(Path("/status")) => Ok
    case GET(Path("/distribution")) => Ok
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
