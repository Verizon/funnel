package funnel
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
  def apply(m: SocketBuilder, u: URI): Throwable \/ Endpoint =
    Location(u).map(Endpoint(m, _))

  def unsafeApply(m: SocketBuilder, u: URI): Endpoint =
    apply(m,u).fold(e => sys.error(s"Threw an exception: ${e.getMessage} whilst using unsafeApply for uri: $u. Check your arguments."),
                    x => x)
}

