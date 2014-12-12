package oncue.svc.funnel
package zeromq

import org.zeromq.ZMQ, ZMQ.Context, ZMQ.Socket
import scalaz.concurrent.Task

/**
 * Take all of the events happening on the monitoring stream, serialise them to binary
 * and then flush them out down the "PUB" 0mq socket.
 */
case class Publisher(
  protocol: String = "tcp",
  host: String = "*",
  port: Int = 7931
){

  private val context: Context = ZMQ.context(1)
  private val socket: Socket = context.socket(ZMQ.PUB)

  def setup(): Unit = {
    try {
      socket.bind(s"$protocol://$host:$port")
      ()
    } catch {
      case e: Throwable => println(s"ERRRROOOOORRRRR - $e")
    }
  }

  def destroy(): Unit = {
    try {
      socket.close()
      context.close()
    } catch {
      case e: java.nio.channels.ClosedChannelException => ()
    }
  }

  import http.{SSE,JSON}, JSON._

  // TODO: implement real serialisation here rather than using the JSON from `http` module
  private def datapointToWireFormat(d: Datapoint[Any]):  Array[Byte] =
    s"${SSE.dataEncode(d)(EncodeDatapoint[Any])}\n".getBytes("UTF-8")

  def publish(M: Monitoring)(implicit log: String => Unit): Task[Unit] = {
    Monitoring.subscribe(M)(_ => true).map(datapointToWireFormat).evalMap { pt =>
      println(pt)
      Task(socket.send(pt))(Monitoring.defaultPool)
    }.run
  }

  setup()
}


