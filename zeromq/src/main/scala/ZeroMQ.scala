package oncue.svc.funnel
package zeromq

import org.zeromq.ZMQ, ZMQ.Context, ZMQ.Socket
import scalaz.concurrent.Task
import scalaz.stream.{Process,Channel,io}
import scalaz.stream.async.mutable.Signal
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

case class Transported(
  version: Version,
  bytes: Array[Byte]
)

object Transported {
  import Versions._

  def apply(h: String, body: String): Transported = {
    val v = for {
      a <- h.split('/').lastOption
      b <- Versions.fromString(a)
    } yield b

    Transported(v.getOrElse(Versions.unknown), body.getBytes)
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
  private[zeromq] val UTF8 = java.nio.charset.Charset.forName("UTF-8")

  /////////////////////////////// USAGE ///////////////////////////////////

  object monitoring {
    import http.JSON._
    import argonaut.EncodeJson
    import scalaz.stream.async.signalOf
    import scalaz.{-\/,\/-}

    // TODO: implement binary serialisation here rather than using the JSON from `http` module
    private def dataEncode[A](a: A)(implicit A: EncodeJson[A]): String =
      A(a).nospaces

    private def datapointToWireFormat(d: Datapoint[Any]): Array[Byte] =
      s"${dataEncode(d)(EncodeDatapoint[Any])}\n".getBytes(UTF8)

    def fromMonitoring(M: Monitoring)(implicit log: String => Unit): Process[Task, Array[Byte]] =
      Monitoring.subscribe(M)(_ => true).map(datapointToWireFormat)

    def fromMonitoringDefault(implicit log: String => Unit): Process[Task, Array[Byte]] =
      fromMonitoring(Monitoring.default)

    private[zeromq] val alive: Signal[Boolean] = signalOf[Boolean](true)

    def stop: Task[Unit] = {
      for {
        _ <- alive.set(false)
        _ <- alive.close
      } yield ()
    }

    // unsafe!
    def to(endpoint: Endpoint): Unit =
      link(endpoint)(alive)(socket =>
        fromMonitoringDefault(m => log.debug(m))
          .through(write(socket))
          .onComplete(Process.eval(Ø.monitoring.stop))
      ).run.runAsync(_ match {
        case -\/(err) => log.error(s"Unable to stream monitoring events to the domain socket: $err")
        case \/-(win) => log.info("Streaming monitoring datapoints to the domain socket.")
      })

    def toUnixSocket(socket: String): Unit =
      to(Endpoint(`Push+Connect`, Address(IPC, host = socket)))

    def toUnixSocket: Unit = toUnixSocket("/var/run/funnel.socket")
  }

  object remote {
    def subscribe(from: Address): Process[Task, Datapoint[Any]] = {
      Ø.link(Endpoint(SubscribeAll, from))(Ø.monitoring.alive)(Ø.receive)
        .flatMap(fromTransported)
    }

    // fairly ugly hack, but it works for now
    def fromTransported(t: Transported): Process[Task, Datapoint[Any]] = {
      import http.JSON._, http.SSE
      t.version match {
        case Versions.v1 =>
          try Process.emit(SSE.parseOrThrow[Datapoint[Any]](new String(t.bytes)))
          catch {
            case e: Exception => Process.fail(e)
          }
        case Versions.v2 => sys.error("not implemented yet!")
      }
    }

  }

  /////////////////////////////// PRIMITIVES ///////////////////////////////////

  def link[O](e: Endpoint
    )(k: Signal[Boolean]
    )(f: Socket => Process[Task,O]): Process[Task, O] =
    resource(setup(e))(r => destroy(r, e)){ connection =>
      haltWhen(k.continuous){
        Process.eval(e.configure(connection.socket)
          ).flatMap(_ => f(connection.socket))
      }
    }

  def receive(socket: Socket): Process[Task, Transported] = {
    Process.eval(Task.now {
      // for the native c++ implementation:
      val header: String = socket.recvStr(UTF8)
      val body: String   = socket.recvStr(UTF8)
      // for the java implementation:
      // val header = socket.recvStr(0)
      // val body   = socket.recvStr(0)
      Transported(header, body)
    }) ++ receive(socket)
  }

  def write(socket: Socket): Channel[Task, Array[Byte], Boolean] =
    io.channel(bytes =>
      Task.delay {
        // log.debug(s"Sending ${bytes.length}")
        socket.sendMore("FMS/1")
        val bool = socket.send(bytes, 0) // the zero here is "flags"
        bool
      }
    )

  /////////////////////////////// INTERNALS ///////////////////////////////////

  private[zeromq] def haltWhen[O](
    kill: Process[Task,Boolean])(
    input: Process[Task,O]
  ): Process[Task,O] =
    kill.zip(input).takeWhile(_._1).map(_._2)

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

  private[zeromq] def destroy(c: Connection, e: Endpoint): Task[Unit] =
    Task.delay {
      log.info(s"Destroying connection for endpoint: $e")
      try {
        c.socket.close()
        c.context.close()
      } catch {
        case e: java.nio.channels.ClosedChannelException => ()
      }
    }
}
