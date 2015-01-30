package oncue.svc.funnel
package zeromq

import org.zeromq.ZMQ, ZMQ.Context, ZMQ.Socket
import scalaz.concurrent.Task
import scalaz.stream.{Process,Channel,io}
import scalaz.stream.async.mutable.Signal
import journal.Logger
import java.net.URI

/**
 * Model 0mq as a set of streams. Primary API should be the `link` function.
 * Example usage:
 *
 * ```
 * Ø.link(E)(K)(Ø.receive).map(new String(_)).to(io.stdOut)
 * ```
 */
object ZeroMQ {
  private[zeromq] val log = Logger[ZeroMQ.type]

  /**
   * A simple pair of Context and Socket. This makes referencing these two things
   * just a bit nicer when it comes to doing resource deallocation upon
   * stream completation.
   */
  case class Connection(
    socket: Socket,
    context: Context
  )

  /////////////////////////////// PRIMITIVES ///////////////////////////////////

  def linkP[O](e: Endpoint
    )(k: Process[Task,Boolean]
    )(f: Socket => Process[Task,O]): Process[Task, O] =
    resource(setup(e))(r => destroy(r, e)){ connection =>
      haltWhen(k){
        f(connection.socket)
      }
    }

  def link[O](e: Endpoint
    )(k: Signal[Boolean]
    )(f: Socket => Process[Task,O]): Process[Task, O] =
    linkP(e)(k.continuous)(f)

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

  /**
   *
   */
  private[zeromq] def haltWhen[O](
    kill: Process[Task,Boolean])(
    input: Process[Task,O]
  ): Process[Task,O] =
    kill.zip(input).takeWhile(_._1).map(_._2)

  /**
   *
   */
  private[zeromq] def resource[F[_],R,O](
    acquire: F[R])(
    release: R => F[Unit])(
    proc: R => Process[F,O]
  ): Process[F,O] =
    Process.eval(acquire).flatMap { r =>
      proc(r).onComplete(Process.eval_(release(r)))
    }

  /**
   *
   */
  private[zeromq] def setup(
    endpoint: Endpoint,
    threadCount: Int = 1
  ): Task[Connection] = {
    log.info(s"Setting up endpoint '${endpoint.location.uri}'...")
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
