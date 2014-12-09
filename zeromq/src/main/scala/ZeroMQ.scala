package oncue.svc.funnel
package zeromq

import org.zeromq.ZMQ, ZMQ.Context, ZMQ.Socket
import scalaz.concurrent.Task

/**
 * Take all of the events happening on the monitoring stream, serialise them to binary
 * and then flush them out down the "PUB" 0mq socket.
 */
case class Publisher(host: String = "*", port: Int = 5775){

  private val context: Context = ZMQ.context(1)
  private val socket: Socket = context.socket(ZMQ.PUB)

  // TODO: implement some scodec magic here.
  private def datapointToBinary(d: Datapoint[Any]): Array[Byte] = Array()

  def publish(M: Monitoring)(Z: Socket)(implicit log: String => Unit): Task[Unit] = {
    socket.bind(s"tcp://$host:$port")

    Monitoring.subscribe(M)(_ => true).map(datapointToBinary
      ).zipWithIndex.evalMap { case (pt,i) =>
        Task {
          Z.send("fooooo")
        }(Monitoring.defaultPool)
    }.run
  }
}


