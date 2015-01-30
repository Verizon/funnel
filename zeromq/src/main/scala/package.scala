package oncue.svc.funnel
package object zeromq {
  import scalaz.concurrent.Task
  import org.zeromq.ZMQ.{Context,Socket}

  val Ø = ZeroMQ

  type SocketBuilder = org.zeromq.ZMQ.Context => Location => Task[Socket]

  private[zeromq] def errorHandler: PartialFunction[Throwable,Task[Socket]] = {
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
}
