package oncue.svc.funnel.agent

import oncue.svc.funnel.{Units,Instrument,Instruments,Reportable,Counter,Timer,Periodic,Stats}
import scalaz.concurrent.Task

import unfiltered.request._
import unfiltered.response._
import unfiltered.netty._
import unfiltered.directives._, Directives._
import java.util.concurrent.ConcurrentHashMap

trait RemoteInstruments {
  import collection.JavaConverters._

  type MetricName = String

  val counters = new ConcurrentHashMap[MetricName, Counter[Periodic[Double]]]

  // def fooooo = ()

  // H.get("metric-name") match {
  //   case Counter(Periodic(n,s,p)) => null
  // }

  // val timers   = new ConcurrentHashMap[String, Timer[Periodic[Stats]]]

  def keys: Set[String] =
    counters.keySet.asScala.toSet //++
}

@io.netty.channel.ChannelHandler.Sharable
object RemoteInstruments extends cycle.Plan with cycle.SynchronousExecution with ServerErrorResponse {

  def intent = Directive.Intent.Path {
    case Seg("metrics" :: Nil) =>
      for {
        _ <- POST
      } yield Ok ~> JsonContent ~> ResponseString("Hello, World.")
  }
}
