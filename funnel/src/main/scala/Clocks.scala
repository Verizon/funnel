package intelmedia.ws.monitoring

import java.util.concurrent.{ExecutorService, ScheduledExecutorService}
import scalaz.stream.Process
import scala.concurrent.duration._

object Clocks {

  /** Add `now/elapsed`, `now/remaining`, and `uptime` metrics. */
  def instrument(I: Instruments)(
    implicit ES: ExecutorService = Monitoring.defaultPool,
             TS: ScheduledExecutorService = Monitoring.schedulingPool): Unit = {
    val elapsed = I.currentElapsed("now/elapsed")
    val remaining = I.currentRemaining("now/remaining")
    val uptime = I.uptime("uptime")
    Process.awakeEvery(2 seconds)(ES,TS).map { _ =>
      elapsed.set(())
      remaining.set(())
      uptime.set(())
    }.run.runAsync(_ => ())
  }
}
