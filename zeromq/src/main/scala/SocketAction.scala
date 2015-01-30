package oncue.svc.funnel
package zeromq

import org.zeromq.ZMQ, ZMQ.Context, ZMQ.Socket
import scalaz.concurrent.Task

abstract class SocketAction extends (Socket => Location => Task[Socket]){ self =>
  def ~(f: Socket => Task[Unit]): SocketAction =
    new SocketAction {
      def apply(s: Socket): Location => Task[Socket] = location =>
        for {
          a <- self.apply(s)(location)
          _ <- f(s)
        } yield s
    }
}

object SocketAction {
  object connect extends SocketAction { self =>
    def apply(s: Socket): Location => Task[Socket] =
      location => Task.delay { s.connect(location.uri.toString); s } //.handleWith(errorHandler)
  }

  object bind extends SocketAction {
    def apply(s: Socket): Location => Task[Socket] =
      location => Task.delay { s.bind(location.uri.toString); s } //.handleWith(errorHandler)
  }

  object topics {
    def specific(discriminator: Array[Byte]): Socket => Task[Unit] =
      s => Task.delay(s.subscribe(discriminator)) //.handleWith(errorHandler)

    def all: Socket => Task[Unit] =
      specific(Array.empty[Byte])
  }
}

object Foo {
  def main(args: Array[String]): Unit = {
    import SocketAction._, SocketMode._

    push &&& (connect ~ topics.all)
    pull &&& bind
    pull &&& connect
  }
}


// abstract class Mode(val asInt: Int){
//   def configure(a: Location, s: Socket): Task[Unit]
//   def errorHandler: PartialFunction[Throwable,Task[Unit]] = {
//     case e: java.io.FileNotFoundException => {
//       Ø.log.error("Unable to bind to the spcified file location. "+
//                   "Please ensure the path to the file you're writing actually exists.")
//       Task.fail(e)
//     }
//     case e: Exception => {
//       Ø.log.error(s"Unable to configure the specified socket mode '$asInt': $e - message: ${e.getMessage}")
//       e.printStackTrace()
//       Task.fail(e)
//     }
//   }
// }

// case object Publish extends Mode(ZMQ.PUB){
//   def configure(a: Location, s: Socket): Task[Unit] =
//     Task.delay {
//       Ø.log.debug("Configuring Publish... " + a.uri.toString)
//       s.bind(a.uri.toString)
//       ()
//     }.handleWith(errorHandler)
// }

// case object SubscribeAll extends Mode(ZMQ.SUB){
//   def configure(a: Location, s: Socket): Task[Unit] =
//     Task.delay {
//       Ø.log.debug("Configuring SubscribeAll... " + a.uri.toString)
//       s.connect(a.uri.toString)
//       s.subscribe(Array.empty[Byte])
//     }.handleWith(errorHandler)
// }

// case object `Push+Connect` extends Mode(ZMQ.PUSH){
//   def configure(a: Location, s: Socket): Task[Unit] =
//     Task.delay {
//       Ø.log.debug("Configuring Push+Connect... " + a.uri.toString)
//       s.connect(a.uri.toString)
//     }.handleWith(errorHandler)
// }

// case object `Pull+Bind` extends Mode(ZMQ.PULL) {
//   def configure(a: Location, s: Socket): Task[Unit] =
//     Task.delay {
//       Ø.log.debug("Configuring Pull+Bind... " + a.uri.toString)
//       s.bind(a.uri.toString)
//       ()
//     }.handleWith(errorHandler)
// }
