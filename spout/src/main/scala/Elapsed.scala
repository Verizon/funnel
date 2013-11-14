package intelmedia.ws.monitoring

import java.util.concurrent.{ExecutorService, ScheduledExecutorService}
import scalaz.stream.Process
import scala.concurrent.duration._

object Elapsed {

  /** Add `now/elapsed` and `uptime` metrics. */
  def instrument(I: Instruments)(
    implicit ES: ExecutorService = Monitoring.defaultPool,
             TS: ScheduledExecutorService = Monitoring.schedulingPool): Unit = {
    val now = I.currentElapsed("now/elapsed")
    val uptime = I.uptime("uptime")
    Process.awakeEvery(2 seconds)(ES,TS).map { _ =>
      now.set(())
      uptime.set(())
    }.run.runAsync(_ => ())
  }
}
