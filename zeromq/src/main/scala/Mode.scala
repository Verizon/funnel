package oncue.svc.funnel
package zeromq

import org.zeromq.ZMQ, ZMQ.Context, ZMQ.Socket
import scalaz.concurrent.Task
import scalaz.stream.{Process,Channel,io}
import scalaz.\/

object Foo {

  private def errorHandler: PartialFunction[Throwable,Task[Unit]] = {
    case e: java.io.FileNotFoundException => {
      Ø.log.error("Unable to bind to the spcified file location. "+
                  "Please ensure the path to the file you're writing actually exists.")
      Task.fail(e)
    }
    case e: Exception => {
      Ø.log.error(s"Unable to configure the specified socket. Error: ${e.getMessage}")
      e.printStackTrace()
      Task.fail(e)
    }
  }

  abstract class SocketAction extends (Socket => Location => Task[Unit]){ self =>
    def ~(f: Socket => Task[Unit]): SocketAction =
      new SocketAction {
        def apply(s: Socket): Location => Task[Unit] = location =>
          self.apply(s)(location).flatMap(_ => f(s))
      }
  }

  object connect extends SocketAction { self =>
    def apply(s: Socket): Location => Task[Unit] =
      location => Task.delay(s.connect(location.uri.toString))
  }

  object topics {
    def specific(discriminator: Array[Byte]): Socket => Task[Unit] =
      s => Task.delay(s.subscribe(discriminator))

    def all: Socket => Task[Unit] =
      specific(Array.empty[Byte])
  }

  object bind extends SocketAction {
    def apply(s: Socket): Location => Task[Unit] =
      location => Task.delay {
        s.bind(location.uri.toString)
      }
  }

  abstract class SocketMode(val asInt: Int) extends (Context => Task[Socket]){
    def apply(ctx: Context): Task[Socket] =
      Task.delay(ctx.socket(asInt))

    def &&&(f: Socket => Location => Task[Unit]): Context => Location => Task[Unit] =
      ctx => location => apply(ctx).flatMap(socket => f(socket)(location))
  }

  object push extends SocketMode(ZMQ.PUSH)
  object pull extends SocketMode(ZMQ.PULL)
  object publish extends SocketMode(ZMQ.PUB)
  object subscribe extends SocketMode(ZMQ.SUB)

  def main(args: Array[String]): Unit = {
    val f: Context => Location => Task[Unit] = push &&& (connect ~ topics.all)
  }
}


abstract class Mode(val asInt: Int){
  def configure(a: Location, s: Socket): Task[Unit]
  def errorHandler: PartialFunction[Throwable,Task[Unit]] = {
    case e: java.io.FileNotFoundException => {
      Ø.log.error("Unable to bind to the spcified file location. "+
                  "Please ensure the path to the file you're writing actually exists.")
      Task.fail(e)
    }
    case e: Exception => {
      Ø.log.error(s"Unable to configure the specified socket mode '$asInt': $e - message: ${e.getMessage}")
      e.printStackTrace()
      Task.fail(e)
    }
  }
}

case object Publish extends Mode(ZMQ.PUB){
  def configure(a: Location, s: Socket): Task[Unit] =
    Task.delay {
      Ø.log.debug("Configuring Publish... " + a.uri.toString)
      s.bind(a.uri.toString)
      ()
    }.handleWith(errorHandler)
}

case object SubscribeAll extends Mode(ZMQ.SUB){
  def configure(a: Location, s: Socket): Task[Unit] =
    Task.delay {
      Ø.log.debug("Configuring SubscribeAll... " + a.uri.toString)
      s.connect(a.uri.toString)
      s.subscribe(Array.empty[Byte])
    }.handleWith(errorHandler)
}

case object `Push+Connect` extends Mode(ZMQ.PUSH){
  def configure(a: Location, s: Socket): Task[Unit] =
    Task.delay {
      Ø.log.debug("Configuring Push+Connect... " + a.uri.toString)
      s.connect(a.uri.toString)
    }.handleWith(errorHandler)
}

case object `Pull+Bind` extends Mode(ZMQ.PULL) {
  def configure(a: Location, s: Socket): Task[Unit] =
    Task.delay {
      Ø.log.debug("Configuring Pull+Bind... " + a.uri.toString)
      s.bind(a.uri.toString)
      ()
    }.handleWith(errorHandler)
}
