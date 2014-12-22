package oncue.svc.funnel.agent

import oncue.svc.funnel.{Units,Instrument,Instruments,Reportable}
import scalaz.concurrent.Task

import unfiltered.request._
import unfiltered.response._
import unfiltered.netty._
import unfiltered.directives._, Directives._
import java.util.concurrent.ConcurrentHashMap

@io.netty.channel.ChannelHandler.Sharable
object RemoteInstruments extends cycle.Plan with cycle.SynchronousExecution with ServerErrorResponse {

  private val H = new ConcurrentHashMap[String, Instrument[_]]

  import QParams._

  def intent = Directive.Intent.Path {
    case Seg("metrics" :: Nil) =>
      for {
        _ <- POST
      } yield Ok ~> JsonContent ~> ResponseString("Hello, World.")
  }
}
