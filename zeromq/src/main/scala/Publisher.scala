package oncue.svc.funnel
package zeromq

import org.zeromq.ZMQ, ZMQ.Context, ZMQ.Socket
import scalaz.concurrent.Task
import scalaz.stream.Process

/**
 * Take all of the events happening on the monitoring stream, serialise them to binary
 * and then flush them out down the "PUB" 0mq socket.
 */
case class Publisher(
  protocol: String = "tcp",
  host: String = "*",
  port: Int = 7931
){

  def setup(threadCount: Int = 1): Task[(Socket,Context)] = Task.delay {
    val context: Context = ZMQ.context(threadCount)
    val socket: Socket = context.socket(ZMQ.PUB)
    socket.bind(s"$protocol://$host:$port")
    (socket,context)
  }

  def destroy(s: Socket, c: Context): Task[Unit] =
    Task.delay {
      try {
        s.close()
        c.close()
      } catch {
        case e: java.nio.channels.ClosedChannelException => ()
      }
    }

  import http.{SSE,JSON}, JSON._

  // TODO: implement real serialisation here rather than using the JSON from `http` module
  private def datapointToWireFormat(d: Datapoint[Any]):  Array[Byte] =
    s"${SSE.dataEncode(d)(EncodeDatapoint[Any])}\n".getBytes("UTF-8")

  def publish(M: Monitoring, S: Socket)(implicit log: String => Unit): Process[Task,Boolean] =
    Monitoring.subscribe(M)(_ => true).map(datapointToWireFormat).evalMap { pt =>
      Task.delay(S.send(pt))
    }
}
