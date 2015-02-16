package funnel
package zeromq

import org.zeromq.ZMQ, ZMQ.Context, ZMQ.Socket
import scalaz.concurrent.Task

abstract class SocketMode(val asInt: Int) extends (Context => Task[Socket]){
  def apply(ctx: Context): Task[Socket] =
    Task.delay(ctx.socket(asInt))

  def &&&(f: Socket => Location => Task[Socket]): SocketBuilder =
    ctx => location => apply(ctx).flatMap(socket => f(socket)(location))
}

trait SocketModes {
  object push extends SocketMode(ZMQ.PUSH)
  object pull extends SocketMode(ZMQ.PULL)
  object publish extends SocketMode(ZMQ.PUB)
  object subscribe extends SocketMode(ZMQ.SUB)
}
