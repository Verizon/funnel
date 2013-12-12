package intelmedia.ws.funnel
package riemann

import scalaz.stream.Process
import scala.concurrent.duration._

object Main {
  private def randomLight(tl: TrafficLight) = 
    util.Random.nextInt(3) match {
      case 1 => tl.yellow
      case 2 => tl.green
      case _ => tl.red
    }

  def main(args: Array[String]): Unit = {
    
    import instruments._

    val R = com.aphyr.riemann.client.RiemannClient.tcp("127.0.0.1", 5555)

    R.connect() // give me stregth!

    Riemann.publish(Monitoring.default, 10f, Events.every(10 seconds))(R)

    val c = counter("requests")
    val t = timer("response-time")

    val l = trafficLight("stoplight")

    val g = Process.awakeEvery(2 seconds).map { _ =>
      c.increment
      t.time(Thread.sleep(100))
      randomLight(l)
    }.run.run

  }
}
