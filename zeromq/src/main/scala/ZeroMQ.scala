package oncue.svc.funnel
package zeromq

import org.zeromq.ZMQ, ZMQ.Context, ZMQ.Socket
import scalaz.concurrent.Task
import scalaz.stream.{Process,Channel,io}
import scalaz.stream.async.mutable.Signal
import journal.Logger
import java.net.URI

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

  def apply(h: String, bytes: Array[Byte]): Transported = {
    val v = for {
      a <- h.split('/').lastOption
      b <- Versions.fromString(a)
    } yield b

    Transported(v.getOrElse(Versions.unknown), bytes)
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

    // def toUnixSocket(path: String): Unit =
      // to(Endpoint(`Push+Connect`, Location(new URI("ipc://$path"))))

    // def toUnixSocket: Unit = toUnixSocket("/var/run/funnel.socket")
  }

  // object mirror {

  //   import java.net.URI
  //   import java.util.concurrent.{ExecutorService,ScheduledExecutorService}

  //   def subscribe(uri: URI
  //     )(implicit S: ExecutorService = Monitoring.serverPool
  //     ): Process[Task, Datapoint[Any]] =
  //       Endpoint(SubscribeAll, uri).fold(Process.fail(_), l =>
  //         Ø.link(l)(Ø.monitoring.alive)(Ø.receive).flatMap(fromTransported)
  //       )

  //   // fairly ugly hack, but it works for now
  //   def fromTransported(t: Transported): Process[Task, Datapoint[Any]] = {
  //     import http.JSON._, http.SSE
  //     t.version match {
  //       case Versions.v1 =>
  //         try Process.emit(SSE.parseOrThrow[Datapoint[Any]](new String(t.bytes)))
  //         catch {
  //           case e: Exception => Process.fail(e)
  //         }
  //       case Versions.v2 => sys.error("not implemented yet!")
  //     }
  //   }

  // }

  /////////////////////////////// PRIMITIVES ///////////////////////////////////

  // def linkP[O](e: Endpoint
  //   )(k: Process[Task,Boolean]
  //   )(f: Socket => Process[Task,O]): Process[Task, O] =
  //   resource(setup(e))(r => destroy(r, e)){ connection =>
  //     haltWhen(k){
  //       Process.eval(e.configure(connection.socket)
  //         ).flatMap(_ => f(connection.socket))
  //     }
  //   }

  def link[O](e: Endpoint
    )(k: Signal[Boolean]
    )(f: Socket => Process[Task,O]): Process[Task, O] =
    resource(setup(e))(r => destroy(r, e)){ connection =>
      haltWhen(k.continuous){
        f(connection.socket)
      }
    }

  def receive(socket: Socket): Process[Task, Transported] = {
    Process.eval(Task.delay {
      // for the native c++ implementation:
      val header: Array[Byte] = socket.recv
      val body: Array[Byte]   = socket.recv

      // println(">>" + header)

      // for the java implementation:
      // val header = socket.recvStr(0)
      // val body   = socket.recvStr(0)
      Transported(new String(header), body)
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
  ): Task[Connection] = {
    log.info("Setting up endpoint '$endpoint'...")
    for {
      a <- Task.delay(ZMQ.context(threadCount))
      b <- endpoint.configure(a)
    } yield Connection(b,a)
  }

  /**
   * This is truly a totally unmaintainable function, and as such, it needs
   * some documentation to explain what the fuck is going on here.
   *
   * As it turns out, ZeroMQ takes a dump if you try to close the socket context on
   * the same thread from which you terminated the socket itself. So, given
   * that all Tasks in this module use `delay` to delegate thread control to the
   * caller (and subsequently have that Task blocked by whomever is running),
   * we just start an inner Task that we use to dispatch to another thread
   * simply to close the socket.
   *
   * This is ugly, and as far as I can tell, it works.
   */
  private[zeromq] def destroy(c: Connection, e: Endpoint): Task[Unit] =
    Task.delay {
      log.info(s"Destroying connection for endpoint: $e")
      try {
        log.warn(s"Trying to close socket in thread '${Thread.currentThread.getName}'")
        c.socket.close()
        Task {
          log.warn(s"Trying to close context in thread '${Thread.currentThread.getName}'")
          c.context.close()
        }.runAsync(e => log.warn(s"Result of closing context was: $e"))
      } catch {
        case e: java.nio.channels.ClosedChannelException => ()
      }
    }
}
