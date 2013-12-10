package intelmedia.ws.monitoring
package riemann

import scalaz.stream.Process
import scala.concurrent.duration._

object Main {
  def main(args: Array[String]): Unit = {
    
    import instruments._

    val R = com.aphyr.riemann.client.RiemannClient.tcp("127.0.0.1", 5555)

    R.connect() // give me stregth!

    Riemann.publish(Monitoring.default, 10f, Events.every(10 seconds))(R)

    val c = counter("requests")
    val t = timer("response-time")
    val g = Process.awakeEvery(2 seconds).map { _ =>
      c.increment
      t.time(Thread.sleep(100))
    }.run.run

  }
}
