package oncue.svc.funnel
package agent
package statsd

import java.net.InetSocketAddress
import java.util.concurrent.Executors
import io.netty.bootstrap.Bootstrap
import io.netty.channel.{ChannelOption,EventLoopGroup}
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioDatagramChannel

object Server {
  def apply(host: String, port: Int, prefix: String)(I: Instruments){
    val group = new NioEventLoopGroup
    try {
      val b = new Bootstrap
      b.group(group)
        .channel(classOf[NioDatagramChannel])
        .option[java.lang.Boolean](ChannelOption.SO_BROADCAST, true)
        .handler(new Handler(prefix,I))

      b.bind(new InetSocketAddress(host,port))
       .sync()
       .channel()
       .closeFuture()
       .await()
    } finally group.shutdownGracefully()
  }
}
