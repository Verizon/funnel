package oncue.svc.funnel
package zeromq

import org.zeromq.ZMQ, ZMQ.Context, ZMQ.Socket
import scalaz.concurrent.Task
import scalaz.stream.Process

abstract class Protocol(val asString: String)
case object TCP extends Protocol("tcp")
case object IPC extends Protocol("icp")
case object UDP extends Protocol("udp")
case object InProc extends Protocol("proc")
case object Pair extends Protocol("pair")

abstract class Mode(val asInt: Int)
case object Publish extends Mode(ZMQ.PUB)
case object Subscribe extends Mode(ZMQ.SUB)
case object Dealer extends Mode(ZMQ.DEALER)
case object Router extends Mode(ZMQ.ROUTER)

/*
(
  protocol: String = "tcp",
  host: String = "*",
  port: Int = 7931
)
*/

case class Endpoint(
  mode: Mode,
  host: String = "*",
  port: Int
)

/**
 * Take all of the events happening on the monitoring stream, serialise them to binary
 * and then flush them out down the "PUB" 0mq socket.
 */
object ZeroMQ {

  // def publish(protocol: Protocol, host: String, port: Int){}

  def run(p: Protocol, e: Endpoint
    )(m: Monitoring, kill: Process[Task,Boolean]
    )(implicit log: String => Unit): Task[Unit] = {
    for {
      x        <- setup(p,e,threadCount = 1)
      (soc,ctx) = x
      _        <- kill.zip(connect(m,soc)).takeWhile(x => !x._1).map(_._2).run
      _        <- destroy(soc,ctx)
    } yield ()
  }

  def setup(
    protocol: Protocol,
    endpoint: Endpoint,
    threadCount: Int = 1
  ): Task[(Socket,Context)] = Task.delay {
    val context: Context = ZMQ.context(threadCount)
    val socket: Socket = context.socket(endpoint.mode.asInt)
    socket.bind(s"${protocol.asString}://${endpoint.host}:${endpoint.port}")
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

  def connect(M: Monitoring, S: Socket)(implicit log: String => Unit): Process[Task,Boolean] =
    Monitoring.subscribe(M)(_ => true).map(datapointToWireFormat).evalMap { pt =>
      Task.delay(S.send(pt))
    }
}
