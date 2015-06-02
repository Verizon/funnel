package funnel
package agent
package statsd

import scalaz.concurrent.Task
import java.net.InetSocketAddress
// import java.util.concurrent.Executors
import io.netty.bootstrap.Bootstrap
import io.netty.channel.{ChannelOption,EventLoopGroup}
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioDatagramChannel

object Server {
  // never explicitly specify "host" as a paramater here.
  // non-root users cannot accept SO_BROADCAST, so instead,
  // we simply use a wildcard address binding.
  // more information here:
  // https://github.com/netty/netty/issues/576
  def apply(port: Int, prefix: String)(I: Instruments): Task[Unit] =
    Task {
      val group = new NioEventLoopGroup
      try {
        val b = new Bootstrap
        b.group(group)
          .channel(classOf[NioDatagramChannel])
          .option[java.lang.Boolean](ChannelOption.SO_BROADCAST, true)
          .handler(new Handler(prefix,I))

        b.bind(new InetSocketAddress(port))
         .sync()
         .channel()
         .closeFuture()
          .await()
        ()
      } finally group.shutdownGracefully()
    }(Monitoring.serverPool)
}
