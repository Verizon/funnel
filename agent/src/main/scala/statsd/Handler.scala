package oncue.svc.funnel
package agent.statsd

import scala.math.round
import org.jboss.netty.channel.{ExceptionEvent, MessageEvent, ChannelHandlerContext, SimpleChannelUpstreamHandler}
import java.util.concurrent.TimeUnit
import util.matching.Regex

/**
 * This implementation is a derivitive work from the BSD server here:
 * https://github.com/mojodna/metricsd
 */
class Handler(prefix: String) extends SimpleChannelUpstreamHandler {
  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent){
    val msg = e.getMessage.asInstanceOf[String]
  }
}