package oncue.svc.funnel
package agent
package statsd

import java.net.InetSocketAddress
import java.util.concurrent.Executors
import org.jboss.netty.util.CharsetUtil
import org.jboss.netty.bootstrap.ConnectionlessBootstrap
import org.jboss.netty.channel.socket.nio.NioDatagramChannelFactory
import org.jboss.netty.handler.codec.string.{StringDecoder, StringEncoder}
import org.jboss.netty.channel.{FixedReceiveBufferSizePredictorFactory, Channels, ChannelPipeline, ChannelPipelineFactory}

/**
 * This implementation is a derivitive work from the BSD server here:
 * https://github.com/mojodna/metricsd
 */
class Server(port: Int, prefix: String){
  def listen = {
    val f = new NioDatagramChannelFactory(Executors.newCachedThreadPool)
    val b = new ConnectionlessBootstrap(f)

    b.setPipelineFactory(new ChannelPipelineFactory {
      def getPipeline: ChannelPipeline = {
        Channels.pipeline(
          new StringEncoder(CharsetUtil.UTF_8),
          new StringDecoder(CharsetUtil.UTF_8),
          new Handler(prefix)
        )
      }
    })

    b.setOption("broadcast", "false")
    b.setOption("receiveBufferSizePredictorFactory", new FixedReceiveBufferSizePredictorFactory(1024))
    b.bind(new InetSocketAddress(port))
  }
}

object MetricsServer {
  val DefaultPort = 8125
}