package intelmedia.ws.funnel

import java.util.concurrent.{ExecutorService, ScheduledExecutorService}
import scalaz.stream.Process
import scala.concurrent.duration._

object Clocks {

  /** Add `now/elapsed`, `now/remaining`, and `uptime` metrics. */
  def instrument(I: Instruments)(
    implicit ES: ExecutorService = Monitoring.defaultPool,
             TS: ScheduledExecutorService = Monitoring.schedulingPool,
             t: Duration = 5 seconds): Unit = {
    val elapsed =
      I.currentElapsed("now/elapsed", "Time since the last period of ${I.window} ended")
    val remaining =
      I.currentRemaining("now/remaining", "Time until the next period of ${I.window} begins")
    val uptime = I.uptime("uptime")
    Process.awakeEvery(t)(ES,TS).map { _ =>
      elapsed.set(())
      remaining.set(())
      uptime.set(())
    }.run.runAsync(_ => ())
  }
}
