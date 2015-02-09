package oncue.svc.funnel
package agent
package statsd

import scalaz.concurrent.Task
import io.netty.channel.{SimpleChannelInboundHandler,ChannelHandlerContext}
import io.netty.channel.socket.DatagramPacket
import io.netty.util.CharsetUtil

class Handler(prefix: String, I: Instruments) extends SimpleChannelInboundHandler[DatagramPacket]{
  override def channelRead0(ctx: ChannelHandlerContext, packet: DatagramPacket){
    val msg: String = packet.content.toString(CharsetUtil.UTF_8)
    msg.trim.split("\n").foreach { line: String =>
      val task = Parser.toRequest(line)(prefix).fold(
        a => Task.fail(a),
        b => {
          println(">>>>>> " +b+ " <<<<<<<")
          RemoteInstruments.metricsFromRequest(b)(I)
        })

      task.run // unsafe!
    }
  }

  override def channelReadComplete(ctx: ChannelHandlerContext) {
    ctx.flush()
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
    cause.printStackTrace()
    // We don't close the channel because we can keep serving requests.
  }
}
