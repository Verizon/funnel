package oncue.svc.funnel
package zeromq

import org.zeromq.ZMQ, ZMQ.Context, ZMQ.Socket
import scalaz.concurrent.Task
import scalaz.stream.{Process,Channel,io}
import journal.Logger

case class Address(
  protocol: Protocol,
  host: String = "*",
  port: Option[Int] = None){
  override def toString: String =
    port.map(p => s"$protocol://$host:$p"
      ).getOrElse(s"$protocol://$host")
}

object Address {
  def apply(protocol: Protocol, host: String, port: Int): Address =
    Address(protocol, host, Option(port))
}

case class Endpoint(
  mode: Mode,
  address: Address){
  def configure(s: Socket): Task[Unit] =
    mode.configure(address, s)
}

case class Connection(
  socket: Socket,
  context: Context
)

abstract class Version(val number: Int)
object Versions {
  def fromInt(i: Int): Option[Version] =
    i match {
      case 1 => Option(v1)
      case 2 => Option(v2)
      case _ => None
    }

  def fromString(s: String): Option[Version] =
    try fromInt(s.toInt)
    catch {
      case e: NumberFormatException => None
    }

  val all: Seq[Version] = v1 :: v2 :: Nil

  case object `v1` extends Version(1)
  case object `v2` extends Version(2)
  case object `unknown` extends Version(0)
}


case class Transported(
  version: Version,
  payload: String
)
object Transported {
  import Versions._

  def apply(h: String, body: String): Transported = {
    val v = for {
      a <- h.split('/').lastOption
      b <- Versions.fromString(a)
    } yield b

    Transported(v.getOrElse(Versions.unknown), body)
  }
}


/**
 * Model 0mq as a set of streams. Primary API should be the `link` function.
 * Example usage:
 *
 * ```
 * Ø.link(E)(K)(Ø.consume).to(io.stdOut)
 * ```
 */
object ZeroMQ {

  private[zeromq] val log = Logger[ZeroMQ.type]

  def link[O](e: Endpoint
    )(k: Process[Task,Boolean]
    )(f: Socket => Process[Task,O]
  ): Process[Task, O] =
    resource(setup(e))(r => destroy(r)){ connection =>
      haltWhen(k){
        Process.eval(e.configure(connection.socket)
          ).flatMap(_ => f(connection.socket))
      }
    }

  def consume(socket: Socket): Process[Task, Transported] = {
    Process.eval(Task.now {
      val header = socket.recvStr
      val body   = socket.recvStr
      Transported(header, body)
    }) ++ consume(socket)
  }

  def channel(socket: Socket): Channel[Task, Array[Byte], Boolean] =
    io.channel(bytes =>
      Task.delay {
        log.debug(s"Sending ${bytes.length}")
        socket.sendMore("FMS/1")
        socket.send(bytes)
      }
    )

  /////////////////////////////// INTERNALS ///////////////////////////////////

  private[zeromq] def haltWhen[O](
    kill: Process[Task,Boolean])(
    input: Process[Task,O]
  ): Process[Task,O] =
    kill.zip(input).takeWhile(x => !x._1).map(_._2)

  private[zeromq] def resource[F[_],R,O](
    acquire: F[R])(
    release: R => F[Unit])(
    proc: R => Process[F,O]
  ): Process[F,O] =
    Process.eval(acquire).flatMap { r =>
      proc(r).onComplete(Process.eval_(release(r)))
    }

  private[zeromq] def setup(
    endpoint: Endpoint,
    threadCount: Int = 1
  ): Task[Connection] = Task.delay {
    log.info("Setting up endpoint = " + endpoint)
    val context: Context = ZMQ.context(threadCount)
    val socket: Socket = context.socket(endpoint.mode.asInt)
    Connection(socket,context)
  }

  private[zeromq] def destroy(c: Connection): Task[Unit] =
    Task.delay {
      log.info("Destroying connection...")
      try {
        c.socket.close()
        c.context.close()
      } catch {
        case e: java.nio.channels.ClosedChannelException => ()
      }
    }
}

object stream {
  import http.JSON._
  import argonaut.EncodeJson

  // TODO: implement binary serialisation here rather than using the JSON from `http` module
  private def dataEncode[A](a: A)(implicit A: EncodeJson[A]): String =
    A(a).nospaces

  private def datapointToWireFormat(d: Datapoint[Any]):  Array[Byte] =
    s"${dataEncode(d)(EncodeDatapoint[Any])}\n".getBytes("UTF-8")

  def from(M: Monitoring)(implicit log: String => Unit): Process[Task,Array[Byte]] =
    Monitoring.subscribe(M)(_ => true).map(datapointToWireFormat)
}


