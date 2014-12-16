package oncue.svc.funnel
package zeromq

import org.zeromq.ZMQ, ZMQ.Context, ZMQ.Socket

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
case object Push extends Mode(ZMQ.PUSH)
case object Pull extends Mode(ZMQ.PULL)

case class Endpoint(
  mode: Mode,
  host: String = "*",
  port: Int
)

import scalaz.concurrent.Task
import scalaz.stream.{Process,Channel,io}

case class Connection(
  socket: Socket,
  context: Context
)

/**
 * Take all of the events happening on the monitoring stream, serialise them to binary
 * and then flush them out down the "PUB" 0mq socket.
 */
object ZeroMQ {

  def haltWhen[O](kill: Process[Task,Boolean])(input: Process[Task,O]): Process[Task,O] =
    kill.zip(input).takeWhile(x => !x._1).map(_._2)

  def outbound[O,O2](p: Protocol, e: Endpoint)(i: Process[Task,O], k: Process[Task,Boolean], c: Socket => Channel[Task,O,O2]): Task[Unit] =
    for {
      a <- setup(p, e, threadCount = 1)
      _ <- haltWhen(k)(i).through(c(a.socket)).run
      _ <- destroy(a)
    } yield ()

  private def resource[F[_],R,O](
    acquire: F[R])(
    release: R => F[Unit])(
    proc: R => Process[F,O]
  ): Process[F,O] =
    Process.eval(acquire).flatMap { r =>
      proc(r).onComplete(Process.eval_(release(r)))
    }

  def foooo(p: Protocol, e: Endpoint)(k: Process[Task,Boolean]): Process[Task, String] =
    resource(setup(p,e))(r => destroy(r)){ connection =>
      haltWhen(k){
        consume(connection.socket)
      }
    }

  // def inbound(e: Endpoint, p: Protocol)(f: Socket => Task[Socket])

  def consume(socket: Socket): Process[Task, String] =
    Process.eval(Task.delay(socket.recvStr)) ++ consume(socket)

  def setup(
    protocol: Protocol,
    endpoint: Endpoint,
    threadCount: Int = 1
  ): Task[Connection] = Task.delay {
    val context: Context = ZMQ.context(threadCount)
    val socket: Socket = context.socket(endpoint.mode.asInt)
    // socket.bind(s"${protocol.asString}://${endpoint.host}:${endpoint.port}")
    Connection(socket,context)
  }

  def destroy(c: Connection): Task[Unit] =
    Task.delay {
      try {
        c.socket.close()
        c.context.close()
      } catch {
        case e: java.nio.channels.ClosedChannelException => ()
      }
    }

  def channel: Socket => Channel[Task, Array[Byte], Boolean] =
    socket => io.channel(bytes => Task.delay(socket.send(bytes)))

}

object stream {
  import http.{SSE,JSON}, JSON._

  // TODO: implement real serialisation here rather than using the JSON from `http` module
  private def datapointToWireFormat(d: Datapoint[Any]):  Array[Byte] =
    s"${SSE.dataEncode(d)(EncodeDatapoint[Any])}\n".getBytes("UTF-8")

  def from(M: Monitoring)(implicit log: String => Unit): Process[Task,Array[Byte]] =
    Monitoring.subscribe(M)(_ => true).map(datapointToWireFormat)
}


