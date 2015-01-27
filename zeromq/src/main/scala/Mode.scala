package oncue.svc.funnel
package zeromq

import org.zeromq.ZMQ, ZMQ.Context, ZMQ.Socket
import scalaz.concurrent.Task
import scalaz.stream.{Process,Channel,io}
import scalaz.\/

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
