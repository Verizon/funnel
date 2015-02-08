package oncue.svc.funnel
package agent
package statsd

import scala.math.round
// import org.jboss.netty.channel.{ExceptionEvent, MessageEvent, ChannelHandlerContext, SimpleChannelUpstreamHandler}
import java.util.concurrent.TimeUnit
import util.matching.Regex
import io.netty.channel.{SimpleChannelInboundHandler,ChannelHandlerContext}


/**
 * This implementation is a derivitive work from the BSD server here:
 * https://github.com/mojodna/metricsd
 */
class Handler(prefix: String) extends SimpleChannelInboundHandler[String] {
  override def channelRead0(ctx: ChannelHandlerContext, msg: String){
    msg.trim.split("\n").foreach { line: String =>
      // Parser.parse(line)
      // RemoteInstruments.metricsFromRequest()
    }
  }
}
