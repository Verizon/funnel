package oncue.svc.funnel
package zeromq

import org.zeromq.ZMQ, ZMQ.{Context,Socket}
import scalaz.concurrent.Task
import scalaz.\/
import java.net.URI

case class Endpoint(
  builder: SocketBuilder,
  location: Location){
  def configure(ctx: Context): Task[Socket] =
    builder(ctx)(location)
}

object Endpoint {
  def apply(m: SocketBuilder, u: URI): Throwable \/ Endpoint = {
    \/.fromTryCatchNonFatal(Endpoint(m, Location(u)))
  }
}
