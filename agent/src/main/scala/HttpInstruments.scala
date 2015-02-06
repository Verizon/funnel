package oncue.svc.funnel
package agent

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
object HttpInstruments extends cycle.Plan with cycle.SynchronousExecution with ServerErrorResponse {
  import JSON._
  import concurrent.duration._
  import instruments._
  import metrics._

  implicit val I = new Instruments(1.minute)

  private def decode[A : DecodeJson](req: HttpRequest[Any])(f: A => ResponseFunction[Any]) =
    JsonRequest(req).decodeEither[A].map(f).fold(fail => BadRequest ~> ResponseString(fail), identity)

  def intent = {
    case r@Path("/metrics") => r match {
      case POST(_) =>
        http.MetricLatency.time {
          decode[InstrumentRequest](r){ typed =>
            RemoteInstruments.metricsFromRequest(typed)(I).map { _ =>
              Ok ~> JsonResponse("ok.")
            }.or(Task.now(InternalServerError ~> JsonResponse("failed."))).run
          }
        }
      case _ => BadRequest
    }
  }
}
