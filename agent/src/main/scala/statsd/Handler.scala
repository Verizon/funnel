package oncue.svc.funnel
package agent
package statsd

// import scala.math.round
// import util.matching.Regex
// import java.util.concurrent.TimeUnit
import scalaz.concurrent.Task
import io.netty.channel.{SimpleChannelInboundHandler,ChannelHandlerContext}

class Handler(prefix: String)(cluster: String, I: Instruments) extends SimpleChannelInboundHandler[String] {
  override def channelRead0(ctx: ChannelHandlerContext, msg: String){
    msg.trim.split("\n").foreach { line: String =>
      val task = Parser.toRequest(line)(cluster).fold(
        a => Task.fail(a),
        b => RemoteInstruments.metricsFromRequest(b)(I))

      task.run // unsafe!
    }
  }
}
