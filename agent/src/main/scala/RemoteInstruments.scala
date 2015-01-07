package oncue.svc.funnel.agent

import oncue.svc.funnel.{Units,Instrument,Instruments,Counter,Timer,Periodic,Stats}
import scalaz.concurrent.Task
import java.util.concurrent.ConcurrentHashMap
import scalaz.\/

object RemoteInstruments {
  import collection.JavaConverters._
  import scala.reflect.runtime.universe._
  import scala.concurrent.duration._

  type MetricName = String

  private val counters = new ConcurrentHashMap[MetricName, Counter[Periodic[Double]]]
  private val timers   = new ConcurrentHashMap[MetricName, Timer[Periodic[Stats]]]

  private def lookup[A](key: MetricName)(hash: ConcurrentHashMap[MetricName, A]): Option[A] =
    Option(hash.get(key))

  def metricsFromRequest(r: InstrumentRequest)(I: Instruments): Task[Unit] = {
    for {
      _ <- Task.gatherUnordered(r.counters.map(counter(_)(I)))
      _ <- Task.gatherUnordered(r.timers.map(timer(_)(I)))
    } yield ()
  }

  def counter(m: ArbitraryMetric)(I: Instruments): Task[Unit] = {
    val counter = lookup[Counter[Periodic[Double]]](m.name)(counters).getOrElse {
      val c = I.counter(m.name)
      counters.putIfAbsent(m.name, c)
      c
    }

    Task.now(counter.increment)
  }

  def timer(m: ArbitraryMetric)(I: Instruments): Task[Unit] = {
    val timer = lookup[Timer[Periodic[Stats]]](m.name)(timers).getOrElse {
      val t = I.timer(m.name)
      timers.putIfAbsent(m.name, t)
      t
    }

    for {
      d <- Task.now(Duration(m.value.get))
      _ <- Task.now(timer.record(d))
    } yield ()
  }
}
