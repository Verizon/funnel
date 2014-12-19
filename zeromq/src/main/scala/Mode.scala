package oncue.svc.funnel
package zeromq

import org.zeromq.ZMQ, ZMQ.Context, ZMQ.Socket
import scalaz.concurrent.Task
import scalaz.stream.{Process,Channel,io}

abstract class Mode(val asInt: Int){
  def configure(a: Address, s: Socket): Task[Unit]
}

case object Publish extends Mode(ZMQ.PUB){
  def configure(a: Address, s: Socket): Task[Unit] =
    Task.delay {
      Ø.log.debug("Configuring Publish... " + a.toString)
      s.bind(a.toString)
      ()
    }
}

case object SubscribeAll extends Mode(ZMQ.SUB){
  def configure(a: Address, s: Socket): Task[Unit] =
    Task.delay {
      Ø.log.debug("Configuring SubscribeAll... " + a.toString)
      s.connect(a.toString)
      s.subscribe(Array.empty[Byte])
    }
}

case object `Push+Connect` extends Mode(ZMQ.PUSH) {
  def configure(a: Address, s: Socket): Task[Unit] =
    Task.delay {
      Ø.log.debug("Configuring Push... " + a.toString)
      s.connect(a.toString)
    }
}

case object `Pull+Bind` extends Mode(ZMQ.PULL) {
  def configure(a: Address, s: Socket): Task[Unit] =
    Task.delay {
      Ø.log.debug("Configuring Pull... " + a.toString)
      s.bind(a.toString)
      ()
    }
}
