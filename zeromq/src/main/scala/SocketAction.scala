package funnel
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

trait SocketActions {
  object connect extends SocketAction { self =>
    def apply(s: Socket): Location => Task[Socket] =
      location => Task.delay { s.connect(location.uri.toString); s }
  }

  object bind extends SocketAction {
    def apply(s: Socket): Location => Task[Socket] =
      location => Task.delay { s.bind(location.uri.toString); s }
  }

  object topics {
    def specific(discriminator: List[Array[Byte]]): Socket => Task[Unit] =
      s => Task.delay(discriminator.foreach(s.subscribe))

    def all: Socket => Task[Unit] =
      specific(List(Array.empty[Byte]))
  }
}
